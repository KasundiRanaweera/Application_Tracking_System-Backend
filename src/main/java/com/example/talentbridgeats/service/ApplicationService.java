package com.example.talentbridgeats.service;

import com.example.talentbridgeats.dto.*;
import com.example.talentbridgeats.exception.*;
import com.example.talentbridgeats.model.*;
import com.example.talentbridgeats.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.core.io.Resource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationNoteRepository applicationNoteRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final PipelineValidator pipelineValidator; // ← injected
    private final ResumeStorageService resumeStorageService;

    public Resource getAuthorizedResume(String filename) {
        Application application = applicationRepository.findByResumeUrlEndingWith(filename)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        Long currentUserId = com.example.talentbridgeats.util.SecurityUtils.getCurrentUserId();
        boolean recruiter = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_RECRUITER"));
        if (!recruiter && !application.getCandidate().getId().equals(currentUserId)) {
            throw new AccessDeniedException("You cannot access this resume");
        }
        return resumeStorageService.load(filename);
    }

    // Candidate: Apply to a job
    public ApplicationSummaryResponseDto apply(ApplyRequestDto request, Long candidateId) {
        Job job = jobRepository.findOpenJobById(request.getJobId())
                .orElseThrow(() -> new ResourceNotFoundException("Job not found or not open"));

        User candidate = userRepository.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));

        if (applicationRepository.existsByJobIdAndCandidateId(job.getId(), candidateId)) {
            throw new DuplicateApplicationException("You have already applied to this job");
        }

        Application application = Application.builder()
                .job(job)
                .candidate(candidate)
                .resumeUrl(request.getResumeUrl())
                .coverNote(request.getCoverNote())
                .status(ApplicationStatus.APPLIED)
                .build();

        Application saved = applicationRepository.save(application);
        return mapToSummaryResponse(saved);
    }

    // Candidate: Get own applications
    public Page<ApplicationSummaryResponseDto> getMyApplications(Long candidateId, Pageable pageable) {
        return applicationRepository.findAll(
                (root, query, cb) -> cb.equal(root.get("candidate").get("id"), candidateId),
                pageable
        ).map(this::mapToSummaryResponse);
    }

    // Candidate: Get single own application (owner check)
    public ApplicationSummaryResponseDto getMyApplication(Long applicationId, Long candidateId) {
        Application application = applicationRepository
                .findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        return mapToSummaryResponse(application);
    }

    // Candidate: Withdraw — goes through PipelineValidator with Role.USER
    public void withdrawApplication(Long applicationId, Long candidateId) {
        Application application = applicationRepository
                .findByIdAndCandidateId(applicationId, candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        // Validate: WITHDRAWN is only allowed by USER role
        pipelineValidator.validate(application.getStatus(), ApplicationStatus.WITHDRAWN, Role.USER);

        application.setStatus(ApplicationStatus.WITHDRAWN);
        applicationRepository.save(application);
    }

    // Recruiter: Get all applications for a job
    public Page<ApplicationDetailResponseDto> getApplicationsByJob(Long jobId, Specification<Application> spec, Pageable pageable) {
        Specification<Application> byJob = (root, query, cb) ->
                cb.equal(root.get("job").get("id"), jobId);
        Specification<Application> combined = spec == null ? byJob : byJob.and(spec);

        return applicationRepository.findAll(combined, pageable)
                .map(this::mapToDetailResponse);
    }

    // Recruiter: Get single application detail
    public ApplicationDetailResponseDto getApplicationDetail(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));
        return mapToDetailResponse(application);
    }

    // Recruiter: Rate application
    public ApplicationDetailResponseDto rateApplication(Long applicationId, RatingRequestDto request) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        application.setRating(request.getRating());
        Application updated = applicationRepository.save(application);
        return mapToDetailResponse(updated);
    }

    // Recruiter: Add note
    public NoteResponseDto addNote(Long applicationId, NoteRequestDto request, Long recruiterId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        User recruiter = userRepository.findById(recruiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter not found"));

        ApplicationNote note = ApplicationNote.builder()
                .application(application)
                .recruiter(recruiter)
                .content(request.getContent())
                .build();

        ApplicationNote saved = applicationNoteRepository.save(note);
        return mapNoteToResponse(saved);
    }

    // Recruiter: Change status — goes through PipelineValidator with Role.RECRUITER
    public ApplicationDetailResponseDto changeApplicationStatus(Long applicationId, StatusChangeRequestDto request) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found"));

        // Validate: recruiter making the move
        pipelineValidator.validate(application.getStatus(), request.getStatus(), Role.RECRUITER);

        application.setStatus(request.getStatus());
        Application updated = applicationRepository.save(application);
        return mapToDetailResponse(updated);
    }

    // Map to summary (candidate view — no rating, no notes)
    private ApplicationSummaryResponseDto mapToSummaryResponse(Application app) {
        return ApplicationSummaryResponseDto.builder()
                .id(app.getId())
                .jobId(app.getJob().getId())
                .jobTitle(app.getJob().getTitle())
                .companyName("TalentBridge")
                .status(app.getStatus())
                .appliedAt(app.getAppliedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }

    // Map to detail (recruiter view — rating + notes included)
    private ApplicationDetailResponseDto mapToDetailResponse(Application app) {
        List<NoteResponseDto> notes = applicationNoteRepository.findByApplicationId(app.getId())
                .stream()
                .map(this::mapNoteToResponse)
                .collect(Collectors.toList());

        return ApplicationDetailResponseDto.builder()
                .id(app.getId())
                .jobId(app.getJob().getId())
                .jobTitle(app.getJob().getTitle())
                .candidateId(app.getCandidate().getId())
                .candidateName(app.getCandidate().getName())
                .candidateEmail(app.getCandidate().getEmail())
                .resumeUrl(app.getResumeUrl())
                .coverNote(app.getCoverNote())
                .status(app.getStatus())
                .rating(app.getRating())
                .notes(notes)
                .appliedAt(app.getAppliedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }

    private NoteResponseDto mapNoteToResponse(ApplicationNote note) {
        return NoteResponseDto.builder()
                .id(note.getId())
                .content(note.getContent())
                .recruiterName(note.getRecruiter().getName())
                .createdAt(note.getCreatedAt())
                .build();
    }
}