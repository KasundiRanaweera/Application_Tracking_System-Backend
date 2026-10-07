-- Creates the table for uploaded CVs (ResumeFile entity) if it does not exist.
-- Runs on every startup before Hibernate initialises, so resume uploads work
-- whatever SPRING_JPA_HIBERNATE_DDL_AUTO is set to (update, validate or none).
CREATE TABLE IF NOT EXISTS tbl_resume_files (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    stored_name   VARCHAR(100) NOT NULL,
    original_name VARCHAR(255),
    content_type  VARCHAR(150) NOT NULL,
    size          BIGINT       NOT NULL,
    data          LONGBLOB     NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_resume_files_stored_name UNIQUE (stored_name)
);
