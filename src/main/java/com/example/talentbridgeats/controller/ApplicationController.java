package com.example.talentbridgeats.controller;

import com.example.talentbridgeats.dto.*;
import com.example.talentbridgeats.model.Application;
import com.example.talentbridgeats.model.ApplicationStatus;
import com.example.talentbridgeats.service.ApplicationService;
import com.example.talentbridgeats.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final com.example.talentbridgeats.service.ResumeStorageService resumeStorageService;

    // ==================== CANDIDATE ENDPOINTS ====================

    //Apply to a job
    @PostMapping
    public ResponseEntity<ApplicationSummaryResponseDto> applyToJob(@Valid @RequestBody ApplyRequestDto request) {
        Long candidateId = SecurityUtils.getCurrentUserId();
        ApplicationSummaryResponseDto application = applicationService.apply(request, candidateId);
        return ResponseEntity.status(HttpStatus.CREATED).body(application);
    }

    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<java.util.Map<String, String>> uploadResume(@RequestParam("file") MultipartFile file) {
        String filename = resumeStorageService.store(file);
        String url = org.springframework.web.servlet.support.ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/applications/resume/")
                .path(filename)
                .toUriString();
        return ResponseEntity.status(HttpStatus.CREATED).body(java.util.Map.of("url", url));
    }

    @GetMapping("/resume/{filename:.+}")
    public ResponseEntity<Resource> downloadResume(@PathVariable String filename) {
        Resource resource = applicationService.getAuthorizedResume(filename);
        String contentType = "application/octet-stream";
        try {
            String detectedContentType = resource.getURL().openConnection().getContentType();
            if (detectedContentType != null) contentType = detectedContentType;
        } catch (Exception ignored) {
            // Fall back to a safe generic content type when file detection fails.
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"resume\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    //Get own applications
    @GetMapping("/me")
    public ResponseEntity<Page<ApplicationSummaryResponseDto>> getMyApplications(Pageable pageable) {
        Long candidateId = SecurityUtils.getCurrentUserId();
        Page<ApplicationSummaryResponseDto> applications = applicationService.getMyApplications(candidateId, pageable);
        return ResponseEntity.ok(applications);
    }

    //Get single own application
    @GetMapping("/me/{id}")
    public ResponseEntity<ApplicationSummaryResponseDto> getMyApplication(@PathVariable Long id) {
        Long candidateId = SecurityUtils.getCurrentUserId();
        ApplicationSummaryResponseDto application = applicationService.getMyApplication(id, candidateId);
        return ResponseEntity.ok(application);
    }

    //Withdraw own application
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> withdrawApplication(@PathVariable Long id) {
        Long candidateId = SecurityUtils.getCurrentUserId();
        applicationService.withdrawApplication(id, candidateId);
        return ResponseEntity.noContent().build();
    }

    // ==================== RECRUITER ENDPOINTS ====================

    //Get all applications for a job (with filtering/sorting)
    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<Page<ApplicationDetailResponseDto>> getJobApplications(
            @PathVariable Long jobId,
            @RequestParam(required = false) ApplicationStatus status,
            Pageable pageable) {

        Specification<Application> spec = null;

        if (status != null) {
            spec = (root, query, cb) -> cb.equal(root.get("status"), status);
        }

        Page<ApplicationDetailResponseDto> applications = applicationService.getApplicationsByJob(jobId, spec, pageable);
        return ResponseEntity.ok(applications);
    }

    //Get single application detail
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ApplicationDetailResponseDto> getApplicationDetail(@PathVariable Long id) {
        ApplicationDetailResponseDto application = applicationService.getApplicationDetail(id);
        return ResponseEntity.ok(application);
    }

    //Rate application
    @PatchMapping("/{id}/rating")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ApplicationDetailResponseDto> rateApplication(
            @PathVariable Long id,
            @Valid @RequestBody RatingRequestDto request) {
        ApplicationDetailResponseDto application = applicationService.rateApplication(id, request);
        return ResponseEntity.ok(application);
    }

    //Add note to application
    @PostMapping("/{id}/notes")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<NoteResponseDto> addNote(
            @PathVariable Long id,
            @Valid @RequestBody NoteRequestDto request) {
        Long recruiterId = SecurityUtils.getCurrentUserId();
        NoteResponseDto note = applicationService.addNote(id, request, recruiterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(note);
    }

    //Change application status
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<ApplicationDetailResponseDto> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusChangeRequestDto request) {
        ApplicationDetailResponseDto application = applicationService.changeApplicationStatus(id, request);
        return ResponseEntity.ok(application);
    }
}