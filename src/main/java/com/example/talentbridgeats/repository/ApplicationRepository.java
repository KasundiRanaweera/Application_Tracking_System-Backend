package com.example.talentbridgeats.repository;

import com.example.talentbridgeats.model.Application;
import com.example.talentbridgeats.model.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long>, JpaSpecificationExecutor<Application> {

    // Find applications by candidate
    List<Application> findByCandidateId(Long candidateId);

    // Find application by id and candidate (owner check)
    Optional<Application> findByIdAndCandidateId(Long id, Long candidateId);

    // Check if candidate already applied to job
    boolean existsByJobIdAndCandidateId(Long jobId, Long candidateId);

    // Find applications for a specific job
    List<Application> findByJobId(Long jobId);

    // Check if application exists and belongs to candidate
    @Query("SELECT a FROM Application a WHERE a.id = :id AND a.candidate.id = :candidateId")
    Optional<Application> findApplicationByCandidateOwnership(@Param("id") Long id, @Param("candidateId") Long candidateId);

    Optional<Application> findByResumeUrlEndingWith(String filename);
}