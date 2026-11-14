ALTER TABLE customer_registrations
    ADD COLUMN role VARCHAR(32) NOT NULL DEFAULT 'USER';
