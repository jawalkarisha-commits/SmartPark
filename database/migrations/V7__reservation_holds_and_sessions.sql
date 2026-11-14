ALTER TABLE bookings
    ADD COLUMN hold_expires_at DATETIME(6) NULL,
    ADD COLUMN actual_entry_time DATETIME(6) NULL,
    ADD COLUMN actual_exit_time DATETIME(6) NULL,
    ADD COLUMN late_hours INT NOT NULL DEFAULT 0,
    ADD COLUMN late_fee_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    ADD INDEX idx_booking_payment_hold (status, hold_expires_at),
    ADD INDEX idx_booking_facility_session (parking_slot_id, status, end_time);
