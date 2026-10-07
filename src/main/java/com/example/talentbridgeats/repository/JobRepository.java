package com.example.talentbridgeats.repository;

import com.example.talentbridgeats.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    // Find only OPEN jobs for candidates
    @Query("SELECT j FROM Job j WHERE j.status = 'OPEN'")
    List<Job> findAllOpenJobs();

    // Find job by id and check if it's OPEN (for candidate detail view)
    @Query("SELECT j FROM Job j WHERE j.id = :id AND j.status = 'OPEN'")
    Optional<Job> findOpenJobById(@Param("id") Long id);

    // Find jobs posted by a recruiter
    List<Job> findByPostedById(Long recruiterId);

    // Check if recruiter owns this job
    boolean existsByIdAndPostedById(Long jobId, Long recruiterId);
}
