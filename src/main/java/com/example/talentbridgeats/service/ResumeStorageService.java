package com.example.talentbridgeats.service;

import com.example.talentbridgeats.exception.ResourceNotFoundException;
import com.example.talentbridgeats.model.ResumeFile;
import com.example.talentbridgeats.repository.ResumeFileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ResumeStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "pdf", "application/pdf",
            "doc", "application/msword",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    /** A loaded resume together with the metadata needed to serve it. */
    public record StoredResume(Resource resource, String contentType, String extension) {}

    private final ResumeFileRepository resumeFileRepository;
    private final Path uploadDirectory;

    public ResumeStorageService(
            ResumeFileRepository resumeFileRepository,
            @Value("${app.upload-dir:${java.io.tmpdir}/talentbridge-uploads}") String uploadDirectory) {
        this.resumeFileRepository = resumeFileRepository;
        this.uploadDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public String store(MultipartFile file) {
        validate(file);

        String extension = getExtension(file.getOriginalFilename());
        String filename = UUID.randomUUID() + "." + extension;
        try {
            resumeFileRepository.save(ResumeFile.builder()
                    .storedName(filename)
                    .originalName(file.getOriginalFilename())
                    .contentType(CONTENT_TYPE_BY_EXTENSION.get(extension))
                    .size(file.getSize())
                    .data(file.getBytes())
                    .build());
            return filename;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store resume file", ex);
        }
    }

    @Transactional(readOnly = true)
    public StoredResume load(String filename) {
        if (filename == null || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid resume file name");
        }
        String extension = getExtension(filename);
        String contentType = CONTENT_TYPE_BY_EXTENSION.getOrDefault(extension, "application/octet-stream");

        var stored = resumeFileRepository.findByStoredName(filename);
        if (stored.isPresent()) {
            ResumeFile resume = stored.get();
            return new StoredResume(new ByteArrayResource(resume.getData()), resume.getContentType(), extension);
        }

        // Fallback for resumes uploaded before database storage was introduced.
        try {
            Path file = uploadDirectory.resolve(filename).normalize();
            if (!file.startsWith(uploadDirectory)) throw new IllegalArgumentException("Invalid resume file name");
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return new StoredResume(resource, contentType, extension);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read resume file", ex);
        }
        throw new ResourceNotFoundException("Resume file is no longer available");
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Resume file is required");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("Resume file must be 5 MB or smaller");

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only PDF, DOC, and DOCX files are allowed");
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported resume file type");
        }
    }

    private String getExtension(String originalFilename) {
        String filename = originalFilename == null ? "" : originalFilename;
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "";
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
