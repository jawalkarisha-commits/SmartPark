CREATE TABLE booking_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_reference VARCHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_booking_group_reference UNIQUE (group_reference),
    CONSTRAINT fk_booking_group_user FOREIGN KEY (user_id) REFERENCES users (id),
    INDEX idx_booking_group_user_created (user_id, created_at)
) ENGINE=InnoDB;

ALTER TABLE bookings
    ADD COLUMN booking_group_id BIGINT NULL;

ALTER TABLE bookings
    ADD INDEX idx_booking_group (booking_group_id);

ALTER TABLE parking_facilities
    DROP INDEX uk_facility_owner;

ALTER TABLE parking_facilities
    ADD COLUMN latitude DECIMAL(10, 7) NULL,
    ADD COLUMN longitude DECIMAL(10, 7) NULL,
    ADD COLUMN active BIT(1) NOT NULL DEFAULT b'1';

ALTER TABLE parking_facilities
    ADD INDEX idx_facility_active_location (active, latitude, longitude);

ALTER TABLE parking_facilities
    ADD INDEX idx_facility_owner (owner_user_id);

ALTER TABLE payments
    MODIFY COLUMN booking_id BIGINT NULL,
    MODIFY COLUMN status VARCHAR(24) NOT NULL;

ALTER TABLE payments
    ADD COLUMN booking_group_id BIGINT NULL;

ALTER TABLE payments
    ADD CONSTRAINT uk_payment_group UNIQUE (booking_group_id);

ALTER TABLE payments
    ADD CONSTRAINT fk_payment_group FOREIGN KEY (booking_group_id) REFERENCES booking_groups (id);

ALTER TABLE payments
    ADD CONSTRAINT chk_payment_parent CHECK (
        (booking_id IS NOT NULL AND booking_group_id IS NULL)
        OR (booking_id IS NULL AND booking_group_id IS NOT NULL)
    );

CREATE TABLE payment_refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    provider_refund_id VARCHAR(64) NULL,
    amount_paise BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    failure_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refund_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_provider_refund_id UNIQUE (provider_refund_id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    INDEX idx_refund_payment_status (payment_id, status)
) ENGINE=InnoDB;

CREATE TABLE payment_webhook_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider_event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    received_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    processing_error VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_webhook_event UNIQUE (provider_event_id)
) ENGINE=InnoDB;

CREATE TABLE late_fee_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    amount_paise BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    razorpay_order_id VARCHAR(64) NOT NULL,
    razorpay_payment_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_late_fee_booking UNIQUE (booking_id),
    CONSTRAINT uk_late_fee_order UNIQUE (razorpay_order_id),
    CONSTRAINT fk_late_fee_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
) ENGINE=InnoDB;
