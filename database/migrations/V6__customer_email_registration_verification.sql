CREATE TABLE customer_registrations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    phone VARCHAR(30) NULL,
    password_hash VARCHAR(255) NOT NULL,
    otp_hash VARCHAR(100) NOT NULL,
    otp_expires_at DATETIME(6) NOT NULL,
    otp_sent_at DATETIME(6) NOT NULL,
    otp_attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customer_registration_email UNIQUE (email),
    INDEX idx_customer_registration_expiration (otp_expires_at)
) ENGINE=InnoDB;
