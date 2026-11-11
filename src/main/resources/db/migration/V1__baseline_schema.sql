CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB;

CREATE TABLE parking_slots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    slot_number VARCHAR(255) NOT NULL,
    floor_number INT NULL,
    vehicle_type VARCHAR(255) NULL,
    available BIT(1) NOT NULL,
    hourly_price DOUBLE NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_parking_slots_number UNIQUE (slot_number)
) ENGINE=InnoDB;

CREATE TABLE vehicles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_name VARCHAR(255) NOT NULL,
    vehicle_number VARCHAR(255) NOT NULL,
    vehicle_type VARCHAR(255) NULL,
    brand VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_vehicles_number UNIQUE (vehicle_number)
) ENGINE=InnoDB;

CREATE TABLE bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    user_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    parking_slot_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_booking_slot_time_status (
        parking_slot_id, status, start_time, end_time
    ),
    CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_bookings_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
    CONSTRAINT fk_bookings_slot FOREIGN KEY (parking_slot_id) REFERENCES parking_slots (id)
) ENGINE=InnoDB;

CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    expires_at DATETIME(6) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    used BIT(1) NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB;
