package com.example.talentbridgeats.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

// Uploaded CV stored in the database so it survives container restarts
// and redeploys (the container file system on Render is ephemeral).
// Table is also created by db/resume-files.sql; keep the two in sync.
@Entity
@Table(name = "tbl_resume_files", uniqueConstraints =
        @UniqueConstraint(name = "uk_resume_files_stored_name", columnNames = "stored_name"))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ResumeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String storedName;

    @Column(length = 255)
    private String originalName;

    @Column(nullable = false, length = 150)
    private String contentType;

    @Column(nullable = false)
    private Long size;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
