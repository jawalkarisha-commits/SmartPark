INSERT INTO parking_facilities (
    id, facility_name, address, total_capacity, operating_start, operating_end, active
) VALUES (
    1, 'Isolated Local Facility', 'Update facility address in Admin Settings',
    25, '00:00:00', '23:59:00', TRUE
);

INSERT INTO smartpark_settings (
    id, currency_code, owner_registration_enabled, vehicle_types,
    cancellation_enabled, cancellation_cutoff_minutes, refund_percentage
) VALUES (
    1, 'INR', TRUE, 'Two-wheeler,Four-wheeler,Electric vehicle',
    TRUE, 0, 100.00
);
