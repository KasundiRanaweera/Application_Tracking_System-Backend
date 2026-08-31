package com.example.talentbridgeats.service;

import com.example.talentbridgeats.dto.JobCreateRequestDto;
import com.example.talentbridgeats.dto.JobResponseDto;
import com.example.talentbridgeats.dto.JobStatusChangeRequestDto;
import com.example.talentbridgeats.dto.JobUpdateRequestDto;
import com.example.talentbridgeats.exception.AccessDeniedException;
import com.example.talentbridgeats.exception.ResourceNotFoundException;
import com.example.talentbridgeats.model.Job;
import com.example.talentbridgeats.model.JobStatus;
import com.example.talentbridgeats.model.Role;
import com.example.talentbridgeats.model.User;
import com.example.talentbridgeats.repository.JobRepository;
import com.example.talentbridgeats.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final UserRepository userRepository;

    // Recruiter: create job
    public JobResponseDto createJob(JobCreateRequestDto request, Long recruiterId) {
        User recruiter = userRepository.findById(recruiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter not found"));

        // Ensure the user is a recruiter
        if (recruiter.getRole() != Role.RECRUITER) {
            throw new AccessDeniedException("Only recruiters can create jobs");
        }

        Job job = Job.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .location(request.getLocation())
                .workMode(request.getWorkMode())
                .employmentType(request.getEmploymentType())
                .salaryMin(request.getSalaryMin())
                .salaryMax(request.getSalaryMax())
                .requiredSkills(request.getRequiredSkills())
                .closingDate(request.getClosingDate())
                .postedBy(recruiter)
                .status(JobStatus.DRAFT)
                .build();

        Job saved = jobRepository.save(job);
        return mapToResponse(saved);
    }

    // Recruiter: update job (only if owner and status is DRAFT)
    public JobResponseDto updateJob(Long jobId, JobUpdateRequestDto request, Long recruiterId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        // Owner check
        if (!job.getPostedBy().getId().equals(recruiterId)) {
            throw new AccessDeniedException("You can only edit your own jobs");
        }

        // Only DRAFT jobs can be updated
        if (job.getStatus() != JobStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT jobs can be updated");
        }

        job.setTitle(request.getTitle() != null ? request.getTitle() : job.getTitle());
        job.setDescription(request.getDescription() != null ? request.getDescription() : job.getDescription());
        job.setLocation(request.getLocation() != null ? request.getLocation() : job.getLocation());
        job.setWorkMode(request.getWorkMode() != null ? request.getWorkMode() : job.getWorkMode());
        job.setEmploymentType(request.getEmploymentType() != null ? request.getEmploymentType() : job.getEmploymentType());
        job.setSalaryMin(request.getSalaryMin() != null ? request.getSalaryMin() : job.getSalaryMin());
        job.setSalaryMax(request.getSalaryMax() != null ? request.getSalaryMax() : job.getSalaryMax());
        job.setRequiredSkills(request.getRequiredSkills() != null ? request.getRequiredSkills() : job.getRequiredSkills());
        job.setClosingDate(request.getClosingDate() != null ? request.getClosingDate() : job.getClosingDate());

        Job updated = jobRepository.save(job);
        return mapToResponse(updated);
    }

    // Recruiter: delete job (only if owner and status is DRAFT)
    public void deleteJob(Long jobId, Long recruiterId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getPostedBy().getId().equals(recruiterId)) {
            throw new AccessDeniedException("You can only delete your own jobs");
        }

        if (job.getStatus() != JobStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT jobs can be deleted");
        }

        jobRepository.delete(job);
    }

    // Recruiter: change job status (DRAFT → OPEN → CLOSED)
    public JobResponseDto changeJobStatus(Long jobId, JobStatusChangeRequestDto request, Long recruiterId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getPostedBy().getId().equals(recruiterId)) {
            throw new AccessDeniedException("You can only change status of your own jobs");
        }

        JobStatus newStatus = request.getStatus();
        JobStatus currentStatus = job.getStatus();

        // Validate transitions: DRAFT → OPEN, OPEN → CLOSED
        if (currentStatus == JobStatus.DRAFT && newStatus == JobStatus.OPEN) {
            job.setStatus(newStatus);
        } else if (currentStatus == JobStatus.OPEN && newStatus == JobStatus.CLOSED) {
            job.setStatus(newStatus);
        } else {
            throw new IllegalStateException("Invalid status transition: " + currentStatus + " → " + newStatus);
        }

        Job updated = jobRepository.save(job);
        return mapToResponse(updated);
    }

    // Candidate: view single OPEN job
    @Transactional(readOnly = true)
    public JobResponseDto getOpenJobById(Long jobId) {
        Job job = jobRepository.findOpenJobById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Open job not found"));
        return mapToResponse(job);
    }

    // Candidate: list all OPEN jobs with filtering
    @Transactional(readOnly = true)
    public Page<JobResponseDto> listOpenJobs(Specification<Job> spec, Pageable pageable) {
        Specification<Job> openJobsOnly = (root, query, cb) ->
                cb.equal(root.get("status"), JobStatus.OPEN);
        Specification<Job> combined = spec == null ? openJobsOnly : openJobsOnly.and(spec);

        return jobRepository.findAll(combined, pageable)
                .map(this::mapToResponse);
    }

    // Recruiter: list all jobs (any status) for management
    @Transactional(readOnly = true)
    public Page<JobResponseDto> listJobsByRecruiter(Long recruiterId, Specification<Job> spec, Pageable pageable) {
        Specification<Job> byRecruiter = (root, query, cb) ->
                cb.equal(root.get("postedBy").get("id"), recruiterId);
        Specification<Job> combined = spec == null ? byRecruiter : byRecruiter.and(spec);

        return jobRepository.findAll(combined, pageable)
                .map(this::mapToResponse);
    }

    private JobResponseDto mapToResponse(Job job) {
        return JobResponseDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .description(job.getDescription())
                .location(job.getLocation())
                .workMode(job.getWorkMode())
                .employmentType(job.getEmploymentType())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .requiredSkills(job.getRequiredSkills())
                .status(job.getStatus())
                .closingDate(job.getClosingDate())
                .postedById(job.getPostedBy().getId())
                .postedByName(job.getPostedBy().getName())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}