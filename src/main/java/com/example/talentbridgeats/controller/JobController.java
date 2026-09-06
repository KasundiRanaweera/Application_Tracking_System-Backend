package com.example.talentbridgeats.controller;

import com.example.talentbridgeats.dto.JobCreateRequestDto;
import com.example.talentbridgeats.dto.JobStatusChangeRequestDto;
import com.example.talentbridgeats.dto.JobUpdateRequestDto;
import com.example.talentbridgeats.dto.JobResponseDto;
import com.example.talentbridgeats.model.Job;
import com.example.talentbridgeats.model.JobStatus;
import com.example.talentbridgeats.model.EmploymentType;
import com.example.talentbridgeats.model.WorkMode;
import com.example.talentbridgeats.service.JobService;
import com.example.talentbridgeats.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    //Candidates browse OPEN jobs
    @GetMapping
    public ResponseEntity<Page<JobResponseDto>> listOpenJobs(
            @RequestParam(required = false) WorkMode workMode,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String search,
            Pageable pageable) {

        Specification<Job> spec = null;

        if (workMode != null) {
            spec = (root, query, cb) -> cb.equal(root.get("workMode"), workMode);
        }
        if (employmentType != null) {
            Specification<Job> employment = (root, query, cb) -> cb.equal(root.get("employmentType"), employmentType);
            spec = spec == null ? employment : spec.and(employment);
        }
        if (location != null && !location.isEmpty()) {
            Specification<Job> locationSpec = (root, query, cb) -> cb.like(root.get("location"), "%" + location + "%");
            spec = spec == null ? locationSpec : spec.and(locationSpec);
        }
        if (search != null && !search.isEmpty()) {
            Specification<Job> searchSpec = (root, query, cb) -> cb.like(root.get("title"), "%" + search + "%");
            spec = spec == null ? searchSpec : spec.and(searchSpec);
        }

        Page<JobResponseDto> jobs = jobService.listOpenJobs(spec, pageable);
        return ResponseEntity.ok(jobs);
    }

    //Candidate view job detail
    @GetMapping("/{id}")
    public ResponseEntity<JobResponseDto> getOpenJobById(@PathVariable Long id) {
        JobResponseDto job = jobService.getOpenJobById(id);
        return ResponseEntity.ok(job);
    }

    //Create job
    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobResponseDto> createJob(@Valid @RequestBody JobCreateRequestDto request) {
        Long recruiterId = SecurityUtils.getCurrentUserId();
        JobResponseDto job = jobService.createJob(request, recruiterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    //Update job
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobResponseDto> updateJob(
            @PathVariable Long id,
            @Valid @RequestBody JobUpdateRequestDto request) {
        Long recruiterId = SecurityUtils.getCurrentUserId();
        JobResponseDto job = jobService.updateJob(id, request, recruiterId);
        return ResponseEntity.ok(job);
    }

    //Delete job
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Void> deleteJob(@PathVariable Long id) {
        Long recruiterId = SecurityUtils.getCurrentUserId();
        jobService.deleteJob(id, recruiterId);
        return ResponseEntity.noContent().build();
    }

    //Change job status
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobResponseDto> changeJobStatus(
            @PathVariable Long id,
            @Valid @RequestBody JobStatusChangeRequestDto request) {
        Long recruiterId = SecurityUtils.getCurrentUserId();
        JobResponseDto job = jobService.changeJobStatus(id, request, recruiterId);
        return ResponseEntity.ok(job);
    }

    //List recruiter's jobs
    @GetMapping("/manage/all")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Page<JobResponseDto>> listRecruiterJobs(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) JobStatus status,
            Pageable pageable) {

        Long recruiterId = SecurityUtils.getCurrentUserId();

        Specification<Job> spec = null;

        if (search != null && !search.isEmpty()) {
            spec = (root, query, cb) -> cb.like(root.get("title"), "%" + search + "%");
        }
        if (status != null) {
            Specification<Job> statusSpec = (root, query, cb) -> cb.equal(root.get("status"), status);
            spec = spec == null ? statusSpec : spec.and(statusSpec);
        }

        Page<JobResponseDto> jobs = jobService.listJobsByRecruiter(recruiterId, spec, pageable);
        return ResponseEntity.ok(jobs);
    }
}