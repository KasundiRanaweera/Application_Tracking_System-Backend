# TalentBridge ATS — Backend API

A **single-company Applicant Tracking System** built with Java 21 + Spring Boot 4.

Candidates browse open jobs and apply through a clean REST API. Recruiters post jobs,
review applicants, rate them, add private internal notes, and drive each candidate
through a defined hiring pipeline — with a strict boundary between what each role can see.

The React frontend is maintained in the separate `talentbridge-frontend` repository and consumes this API.

The backend also supports the CV upload and secure CV viewing/download flow used by the frontend:
- `POST /api/applications/resume` accepts a multipart file upload for the candidate.
- `GET /api/applications/resume/{filename}` serves the uploaded file securely to the owning candidate or a recruiter reviewing an application.

---

## The Two Roles

| Role | How created | What they can do |
|---|---|---|
| **USER** (Candidate) | Self-registration via `POST /api/auth/register` | Browse open jobs, apply once per job, view and withdraw their own applications |
| **RECRUITER** | Seeded on startup — cannot self-register | Post and manage jobs, view all applicants, rate them, add internal notes, advance or reject through the hiring pipeline |

> Nobody can self-register as a recruiter. Recruiter accounts are created by seeding only.

### Seeded Recruiter Accounts (available after first boot)

| Name | Email | Password |
|---|---|---|
| Default Recruiter | `recruiter@talentbridge.com` | `Recruiter@123` |
| Second Recruiter | `recruiter2@talentbridge.com` | `Recruiter2@123` |

---

## Three Decisions That Make This Project Defensible

### 1. Candidate and recruiter views are completely separate

The same `Application` record returns **different JSON** depending on who asks.

- **Candidates** receive `ApplicationSummaryResponseDto` — status, job title, applied date, and update date. No `rating` field. No `notes` field. Not filtered at runtime — those fields do not exist in the DTO at all.
- **Recruiters** receive `ApplicationDetailResponseDto` — everything above plus rating, internal notes, candidate name, candidate email, resume URL, and cover note.

Internal data cannot leak to candidates because it is **absent from the candidate response shape entirely** — not filtered at runtime, structurally absent. This is a design decision, not a runtime check.

### 2. The hiring pipeline enforces rules, not free text

All application status transitions are validated by `PipelineValidator` before any database write occurs.

**Legal forward path:**
```
APPLIED → UNDER_REVIEW → SHORTLISTED → INTERVIEW → OFFER → HIRED
```

**Exit states (available from most active stages):**
```
→ REJECTED  (recruiter only)
→ WITHDRAWN (owning candidate only)
```

**Terminal states:** `HIRED`, `REJECTED`, `WITHDRAWN` — no further transitions allowed from any of these.

`PipelineValidator` also enforces **who** can make each move:
- Only a `RECRUITER` can advance or reject an application
- Only the `USER` (candidate) who owns an application can withdraw it
- A recruiter attempting to withdraw is rejected with `403 Forbidden`
- A candidate attempting to advance is rejected with `403 Forbidden`

28 unit tests in `PipelineValidatorTest` cover every legal transition, every illegal skip,
every terminal state, and every role violation — without requiring a Spring context to run.

### 3. Owner checks on every candidate-facing fetch

A valid JWT proves **who someone is** — not which records they are allowed to access.

Every service method that fetches an application on a candidate's behalf calls
`findByIdAndCandidateId()`, which combines the record lookup and ownership check
in a single database query. A candidate cannot read another candidate's application
by guessing its ID — they receive `404 Not Found` as if the record does not exist,
revealing nothing about whether the ID is valid.

This pattern is applied consistently in:
- `GET /api/applications/me/{id}` — view single own application
- `DELETE /api/applications/{id}` — withdraw own application

---

## How to Run

### Prerequisites
- Java 21
- MySQL 8 running locally
- Maven (or use the included `./mvnw` wrapper)

### Steps

**1. Create the database**
```sql
CREATE DATABASE IF NOT EXISTS talentbridge_ats CHARACTER SET utf8mb4;
```

**2. Check credentials in `src/main/resources/application.properties`**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/talentbridge_ats?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=your_password
```

**3. Run the application**
```bash
./mvnw spring-boot:run
```

The app starts on port **8080**. On first boot, Hibernate creates all tables and
`DataSeeder` inserts both recruiter accounts automatically. No manual schema setup needed.

**4. Open Swagger UI**

[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

All endpoints are documented and testable directly from the browser.

### Authenticating in Swagger UI

1. Expand `POST /api/auth/login` → click **Try it out**
2. Enter recruiter or candidate credentials → click **Execute**
3. Copy the `token` value from the response body
4. Click **Authorize** (top right of the page)
5. Enter `Bearer <your-token>` → click **Authorize**
6. All protected endpoints now work directly from the browser

### CV upload storage

Candidates can upload PDF, DOC, or DOCX CV files up to 5 MB through the application
API. The backend stores the file and saves its generated URL on the application.

For Railway, attach a persistent volume to the **backend service** and set its mount
path to `/data`. Then add this variable to the backend service:

```text
APP_UPLOAD_DIR=/data/uploads
```

The application creates the upload directory automatically. Without a persistent
volume or cloud object storage, uploaded files stored on the container filesystem
may be lost when Railway redeploys or replaces the service.

---

## Testing with Postman

A fully documented Postman collection is included in the `postman/` folder.

### Import steps

1. Open Postman → click **Import**
2. Select `postman/talentbridge-ats.postman_collection.json`
3. Select the **TalentBridge ATS** environment from the top-right dropdown in Postman

### How tokens work in the collection

The collection uses **environment variables** and **auto-save scripts** so you never
need to manually copy and paste tokens between requests.

- Run `POST /api/auth/login` (recruiter) → `{{recruiter_token}}` saves automatically
- Run `POST /api/auth/register` or login (candidate) → `{{candidate_token}}` saves automatically
- All subsequent requests read the token from the environment variable
- Refreshing a login updates the token — no other requests break

### Environment variables

| Variable | Set by | Used by |
|---|---|---|
| `base_url` | Manual (`http://localhost:8080`) | All requests |
| `recruiter_token` | Recruiter login post-script | All recruiter endpoints |
| `candidate_token` | Candidate register/login post-script | All candidate endpoints |
| `recruiter_id` | Recruiter login post-script | Reference |
| `candidate_id` | Candidate login post-script | Reference |
| `job_id` | Create job post-script | Job and application requests |
| `application_id` | Apply post-script | Application requests |

### Endpoints documented in the collection

**Auth (2 requests)**
- `POST /api/auth/register`
- `POST /api/auth/login`

**Jobs (7 requests)**
- `GET /api/jobs` — list open jobs with filters
- `GET /api/jobs/{id}` — view open job detail
- `POST /api/jobs` — create job (recruiter)
- `PUT /api/jobs/{id}` — update job (recruiter)
- `DELETE /api/jobs/{id}` — delete job (recruiter)
- `PATCH /api/jobs/{id}/status` — change status (recruiter)
- `GET /api/jobs/manage/all` — list all own jobs (recruiter)

**Applications (11 requests)**
- `POST /api/applications` — apply to job (candidate)
- `POST /api/applications/resume` — upload a CV (candidate; multipart file, max 5 MB)
- `GET /api/applications/resume/{filename}` — securely view an uploaded CV
- `GET /api/applications/me` — list own applications (candidate)
- `GET /api/applications/me/{id}` — view own application (candidate)
- `DELETE /api/applications/{id}` — withdraw application (candidate)
- `GET /api/applications/job/{jobId}` — list job applicants (recruiter)
- `GET /api/applications/{id}` — view application detail (recruiter)
- `PATCH /api/applications/{id}/rating` — rate application (recruiter)
- `POST /api/applications/{id}/notes` — add internal note (recruiter)
- `PATCH /api/applications/{id}/status` — advance/reject pipeline (recruiter)

---

## Complete API Reference

### Auth — `/api/auth`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/auth/register` | Public | Register a new candidate account |
| POST | `/api/auth/login` | Public | Log in and receive a JWT token (works for both roles) |

### Jobs — `/api/jobs`

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/jobs` | Any logged-in user | List OPEN jobs. Filter by `workMode`, `employmentType`, `location`; keyword `search` on title; supports sorting and pagination |
| GET | `/api/jobs/{id}` | Any logged-in user | View full detail of a single OPEN job. Returns `404` if DRAFT or CLOSED |
| POST | `/api/jobs` | RECRUITER | Create a new job posting. Starts as `DRAFT` |
| PUT | `/api/jobs/{id}` | RECRUITER (owner only) | Update job fields. Only allowed when status is `DRAFT` |
| DELETE | `/api/jobs/{id}` | RECRUITER (owner only) | Delete a job. Only allowed when status is `DRAFT` |
| PATCH | `/api/jobs/{id}/status` | RECRUITER (owner only) | Change status: `DRAFT → OPEN → CLOSED` only |
| GET | `/api/jobs/manage/all` | RECRUITER | List all own jobs regardless of status. Optional `search`, `status`, sorting, and pagination |

### Applications — `/api/applications`

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/applications` | USER | Apply to an open job. Optional `resumeUrl` and `coverNote`. Returns `409` if already applied |
| POST | `/api/applications/resume` | USER | Upload a PDF, DOC, or DOCX CV up to 5 MB and receive its URL |
| GET | `/api/applications/resume/{filename}` | Candidate owner or RECRUITER | View an uploaded CV securely |
| GET | `/api/applications/me` | USER | List own applications with current status. Paginated. No rating or notes in response |
| GET | `/api/applications/me/{id}` | USER | View a single own application. Returns `404` if not owned by caller |
| DELETE | `/api/applications/{id}` | USER | Withdraw own application. Validated by `PipelineValidator` |
| GET | `/api/applications/job/{jobId}` | RECRUITER | List all applications for a job. Filter by `status`, paginated |
| GET | `/api/applications/{id}` | RECRUITER | View full application including candidate info, rating, and all internal notes |
| PATCH | `/api/applications/{id}/rating` | RECRUITER | Assign or update a rating (1–5) |
| POST | `/api/applications/{id}/notes` | RECRUITER | Add a timestamped internal note. Append-only. Never shown to candidates |
| PATCH | `/api/applications/{id}/status` | RECRUITER | Advance or reject. All moves validated by `PipelineValidator` |

---

## Hiring Pipeline Reference

```
              ┌──────────┐
              │ APPLIED  │
              └────┬─────┘
                   │ Recruiter advances
          ┌────────▼────────┐
          │  UNDER_REVIEW   │
          └────────┬────────┘
                   │ Recruiter advances
          ┌────────▼────────┐
          │   SHORTLISTED   │
          └────────┬────────┘
                   │ Recruiter advances
          ┌────────▼────────┐
          │    INTERVIEW    │
          └────────┬────────┘
                   │ Recruiter advances
          ┌────────▼────────┐
          │      OFFER      │
          └────────┬────────┘
                   │ Recruiter advances
          ┌────────▼────────┐
          │      HIRED      │ ← Terminal
          └─────────────────┘

  From any active stage:
  → REJECTED  (recruiter only) ← Terminal
  → WITHDRAWN (candidate only) ← Terminal
```

**Rules enforced by `PipelineValidator`:**
- Skipping stages → `400 Bad Request`
- Backward moves → `400 Bad Request`
- Transitions from terminal states → `400 Bad Request`
- Wrong role making a move → `403 Forbidden`

---

## Project Structure

```
talentbridge-ats/
├── pom.xml
├── README.md
├── mvnw / mvnw.cmd
│
├── postman/
│   └── talentbridge-ats.postman_collection.json
│
└── src/
    ├── main/java/com/example/talentbridgeats/
    │   ├── TalentbridgeAtsApplication.java
    │   │
    │   ├── config/
    │   │   ├── DataSeeder.java              # Seeds two recruiter accounts on startup
    │   │   ├── OpenApiConfig.java           # Swagger UI with JWT bearer auth
    │   │   └── SecurityConfig.java          # JWT filter chain, role-based HTTP access
    │   │
    │   ├── controller/
    │   │   ├── AuthController.java          # /api/auth/**
    │   │   ├── JobController.java           # /api/jobs/**
    │   │   └── ApplicationController.java   # /api/applications/**
    │   │
    │   ├── dto/
    │   │   ├── AuthResponseDto.java
    │   │   ├── RegisterRequestDto.java
    │   │   ├── LoginRequestDto.java
    │   │   ├── JobCreateRequestDto.java
    │   │   ├── JobUpdateRequestDto.java
    │   │   ├── JobStatusChangeRequestDto.java
    │   │   ├── JobResponseDto.java
    │   │   ├── ApplyRequestDto.java
    │   │   ├── ApplicationSummaryResponseDto.java  # Candidate view — no rating/notes
    │   │   ├── ApplicationDetailResponseDto.java   # Recruiter view — full data
    │   │   ├── RatingRequestDto.java
    │   │   ├── NoteRequestDto.java
    │   │   ├── NoteResponseDto.java
    │   │   └── StatusChangeRequestDto.java
    │   │
    │   ├── exception/
    │   │   ├── GlobalExceptionHandler.java
    │   │   ├── AccessDeniedException.java
    │   │   ├── DuplicateApplicationException.java
    │   │   ├── DuplicateEmailException.java
    │   │   └── ResourceNotFoundException.java
    │   │
    │   ├── model/
    │   │   ├── User.java
    │   │   ├── Role.java                    # USER, RECRUITER
    │   │   ├── Job.java
    │   │   ├── JobStatus.java               # DRAFT, OPEN, CLOSED
    │   │   ├── WorkMode.java                # ONSITE, REMOTE, HYBRID
    │   │   ├── EmploymentType.java          # FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP
    │   │   ├── Application.java
    │   │   ├── ApplicationStatus.java       # Full pipeline stages
    │   │   └── ApplicationNote.java
    │   │
    │   ├── repository/
    │   │   ├── UserRepository.java
    │   │   ├── JobRepository.java           # JpaSpecificationExecutor for filtering
    │   │   ├── ApplicationRepository.java   # Owner-check queries + JpaSpecificationExecutor
    │   │   └── ApplicationNoteRepository.java
    │   │
    │   ├── security/
    │   │   ├── JwtService.java              # Generate and validate JWT tokens
    │   │   ├── JwtAuthFilter.java           # Intercepts every request, sets SecurityContext
    │   │   ├── UserDetailsServiceImpl.java
    │   │   └── CustomAccessDeniedHandler.java
    │   │
    │   ├── service/
    │   │   ├── AuthService.java
    │   │   ├── JobService.java
    │   │   ├── ApplicationService.java
    │   │   ├── ResumeStorageService.java       # Validates and stores uploaded CV files
    │   │   └── PipelineValidator.java       # All pipeline rules in one place
    │   │
    │   └── util/
    │       └── SecurityUtils.java           # getCurrentUserId() from SecurityContext
    │
    ├── main/resources/
    │   └── application.properties
    │
    └── test/java/com/example/talentbridgeats/
        ├── TalentbridgeAtsApplicationTests.java
        └── service/
            └── PipelineValidatorTest.java   # 28 unit tests — no Spring context needed
```

---

## Database Schema

Four tables. `tbl_users` uses a `role` column as a discriminator — candidates and recruiters
share the same table and authentication mechanism, differing only in permissions.

```
tbl_users
  id, name, email (UNIQUE), password (bcrypt),
  role (USER | RECRUITER), created_at

tbl_jobs
  id, title, description, location,
  work_mode (ONSITE | REMOTE | HYBRID),
  employment_type (FULL_TIME | PART_TIME | CONTRACT | INTERNSHIP),
  salary_min, salary_max, required_skills,
  status (DRAFT | OPEN | CLOSED),
  closing_date, posted_by (FK → tbl_users),
  created_at, updated_at

tbl_applications
  id, job_id (FK → tbl_jobs), candidate_id (FK → tbl_users),
  resume_url, cover_note,
  status (APPLIED | UNDER_REVIEW | SHORTLISTED | INTERVIEW | OFFER | HIRED | REJECTED | WITHDRAWN),
  rating (1–5, nullable), applied_at, updated_at
  UNIQUE (job_id, candidate_id)  ← prevents duplicate applications at DB level

tbl_application_notes
  id, application_id (FK → tbl_applications),
  recruiter_id (FK → tbl_users),
  content, created_at
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 |
| Security | Spring Security — stateless JWT (jjwt 0.12.7) |
| Persistence | Spring Data JPA + Hibernate |
| Database | MySQL 8 |
| Validation | Jakarta Bean Validation |
| API Docs | SpringDoc OpenAPI 2.8.9 (Swagger UI) |
| API Testing | Postman (collection included in `/postman`) |
| Boilerplate | Lombok |
| Build | Maven (wrapper included) |

---

## Running Tests

```bash
./mvnw test
```

`PipelineValidatorTest` runs as a **pure unit test** — no Spring context, no database, instant.

It covers all 28 cases:
- All 5 legal forward transitions (recruiter advancing candidate)
- Rejection from all 5 active stages (recruiter)
- Withdrawal from all 5 active stages (candidate)
- Illegal skips — e.g. `APPLIED → HIRED` directly
- Backward moves — e.g. `INTERVIEW → APPLIED`
- All 3 terminal states — `HIRED`, `REJECTED`, `WITHDRAWN`
- Role violations — recruiter trying to withdraw, candidate trying to advance or reject

---

