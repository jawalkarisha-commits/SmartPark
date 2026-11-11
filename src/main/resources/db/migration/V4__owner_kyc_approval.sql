CREATE TABLE owner_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    email_verified BIT(1) NOT NULL DEFAULT b'0',
    otp_hash VARCHAR(100) NULL,
    otp_expires_at DATETIME(6) NULL,
    otp_sent_at DATETIME(6) NULL,
    otp_attempts INT NOT NULL DEFAULT 0,
    document_storage_key VARCHAR(36) NULL,
    document_file_name VARCHAR(255) NULL,
    document_content_type VARCHAR(100) NULL,
    document_size BIGINT NULL,
    document_uploaded_at DATETIME(6) NULL,
    approval_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    approval_method VARCHAR(16) NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_owner_verification_user UNIQUE (owner_user_id),
    CONSTRAINT fk_owner_verification_user
        FOREIGN KEY (owner_user_id) REFERENCES users (id),
    CONSTRAINT fk_owner_verification_reviewer
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users (id),
    INDEX idx_owner_verification_status_created (approval_status, created_at)
) ENGINE=InnoDB;

INSERT INTO owner_verifications (owner_user_id, created_at)
SELECT id, COALESCE(created_at, CURRENT_TIMESTAMP(6))
FROM users
WHERE role = 'PARKING_OWNER';
