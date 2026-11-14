ALTER TABLE users
    ADD COLUMN phone VARCHAR(30) NULL,
    ADD COLUMN enabled BIT(1) NOT NULL DEFAULT b'1',
    ADD COLUMN created_at DATETIME(6) NULL;

UPDATE users
SET created_at = CURRENT_TIMESTAMP(6)
WHERE created_at IS NULL;

ALTER TABLE users
    MODIFY COLUMN created_at DATETIME(6) NOT NULL;

ALTER TABLE parking_slots
    ADD COLUMN location VARCHAR(255) NULL,
    ADD COLUMN zone VARCHAR(100) NULL,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    MODIFY COLUMN hourly_price DECIMAL(10, 2) NULL;

UPDATE parking_slots
SET status = 'MAINTENANCE'
WHERE available = b'0';

ALTER TABLE vehicles
    ADD COLUMN user_id BIGINT NULL,
    ADD COLUMN model VARCHAR(255) NULL,
    ADD COLUMN color VARCHAR(80) NULL;

ALTER TABLE vehicles
    ADD INDEX idx_vehicle_user_id (user_id);

ALTER TABLE vehicles
    ADD CONSTRAINT fk_vehicle_owner FOREIGN KEY (user_id) REFERENCES users (id);

UPDATE vehicles v
JOIN (
    SELECT full_name, MIN(id) AS user_id
    FROM users
    GROUP BY full_name
    HAVING COUNT(*) = 1
) u ON u.full_name = v.owner_name
SET v.user_id = u.user_id;

ALTER TABLE bookings
    MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
    ADD COLUMN booking_reference VARCHAR(36) NULL,
    ADD COLUMN hourly_rate DECIMAL(10, 2) NULL,
    ADD COLUMN amount DECIMAL(10, 2) NULL,
    ADD COLUMN created_at DATETIME(6) NULL,
    ADD INDEX idx_booking_user_start_time (user_id, start_time);

UPDATE bookings b
JOIN parking_slots s ON s.id = b.parking_slot_id
SET b.booking_reference = UUID(),
    b.hourly_rate = COALESCE(s.hourly_price, 0),
    b.amount = ROUND(
        TIMESTAMPDIFF(MINUTE, b.start_time, b.end_time) / 60
            * COALESCE(s.hourly_price, 0),
        2
    ),
    b.created_at = CURRENT_TIMESTAMP(6)
WHERE b.booking_reference IS NULL
   OR b.hourly_rate IS NULL
   OR b.amount IS NULL
   OR b.created_at IS NULL;

ALTER TABLE bookings
    MODIFY COLUMN booking_reference VARCHAR(36) NOT NULL,
    MODIFY COLUMN hourly_rate DECIMAL(10, 2) NOT NULL,
    MODIFY COLUMN amount DECIMAL(10, 2) NOT NULL,
    MODIFY COLUMN created_at DATETIME(6) NOT NULL,
    ADD CONSTRAINT uk_booking_reference UNIQUE (booking_reference);
