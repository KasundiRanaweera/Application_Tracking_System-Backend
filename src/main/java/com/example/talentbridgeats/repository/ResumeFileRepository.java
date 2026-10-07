package com.example.talentbridgeats.repository;

import com.example.talentbridgeats.model.ResumeFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumeFileRepository extends JpaRepository<ResumeFile, Long> {
    Optional<ResumeFile> findByStoredName(String storedName);
}
