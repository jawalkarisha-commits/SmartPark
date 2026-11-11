CREATE TABLE parking_facilities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facility_name VARCHAR(160) NOT NULL,
    address VARCHAR(500) NOT NULL,
    total_capacity INT NOT NULL,
    operating_start TIME NOT NULL,
    operating_end TIME NOT NULL,
    owner_user_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_facility_owner UNIQUE (owner_user_id),
    CONSTRAINT fk_facility_owner FOREIGN KEY (owner_user_id) REFERENCES users (id)
) ENGINE=InnoDB;

INSERT INTO parking_facilities (
    facility_name, address, total_capacity, operating_start, operating_end
) VALUES (
    'SmartPark Demo Facility', 'Update facility address in Admin Settings',
    0, '00:00:00', '23:59:00'
);

ALTER TABLE parking_slots
    ADD COLUMN daily_price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN monthly_price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN facility_id BIGINT NULL,
    ADD INDEX idx_parking_slot_facility (facility_id),
    ADD CONSTRAINT fk_parking_slot_facility
        FOREIGN KEY (facility_id) REFERENCES parking_facilities (id);

ALTER TABLE bookings
    ADD COLUMN billing_period VARCHAR(16) NOT NULL DEFAULT 'HOURLY',
    ADD COLUMN billed_rate DECIMAL(10, 2) NULL;

UPDATE parking_slots
SET facility_id = 1,
    daily_price = COALESCE(hourly_price, 0) * 24,
    monthly_price = COALESCE(hourly_price, 0) * 24 * 30;

UPDATE bookings b
JOIN parking_slots s ON s.id = b.parking_slot_id
SET b.billed_rate = s.hourly_price;

UPDATE bookings SET billed_rate = 0.00 WHERE billed_rate IS NULL;

ALTER TABLE bookings
    MODIFY COLUMN billed_rate DECIMAL(10, 2) NOT NULL;

UPDATE parking_facilities
SET total_capacity = (SELECT COUNT(*) FROM parking_slots WHERE facility_id = 1)
WHERE id = 1;

ALTER TABLE parking_slots
    MODIFY COLUMN facility_id BIGINT NOT NULL;

CREATE TABLE smartpark_settings (
    id TINYINT NOT NULL,
    currency_code CHAR(3) NOT NULL DEFAULT 'INR',
    owner_registration_enabled BIT(1) NOT NULL DEFAULT b'1',
    vehicle_types VARCHAR(500) NOT NULL DEFAULT 'Two-wheeler,Four-wheeler,Electric vehicle',
    cancellation_enabled BIT(1) NOT NULL DEFAULT b'1',
    cancellation_cutoff_minutes INT NOT NULL DEFAULT 0,
    refund_percentage DECIMAL(5, 2) NOT NULL DEFAULT 100.00,
    PRIMARY KEY (id),
    CONSTRAINT chk_smartpark_settings_singleton CHECK (id = 1),
    CONSTRAINT chk_refund_percentage CHECK (
        refund_percentage >= 0 AND refund_percentage <= 100
    ),
    CONSTRAINT chk_cancellation_cutoff CHECK (cancellation_cutoff_minutes >= 0)
) ENGINE=InnoDB;

INSERT INTO smartpark_settings (
    id, currency_code, owner_registration_enabled, cancellation_enabled,
    cancellation_cutoff_minutes, refund_percentage
) VALUES (1, 'INR', b'1', b'1', 0, 100.00);

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    amount_paise BIGINT NOT NULL,
    refund_amount_paise BIGINT NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    razorpay_order_id VARCHAR(64) NOT NULL,
    razorpay_payment_id VARCHAR(64) NULL,
    razorpay_refund_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_booking UNIQUE (booking_id),
    CONSTRAINT uk_razorpay_order UNIQUE (razorpay_order_id),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    INDEX idx_payment_status_created (status, created_at)
) ENGINE=InnoDB;
