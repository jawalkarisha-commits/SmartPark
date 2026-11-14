ALTER TABLE bookings
    ADD COLUMN deposit_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN paid_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN deposit_refund_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN platform_fee_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN owner_earnings DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN settlement_paid_at DATETIME(6) NULL;

ALTER TABLE bookings
    ADD INDEX idx_booking_no_show (status, start_time, actual_entry_time);

UPDATE bookings
SET deposit_amount = ROUND(amount / 2, 2);

UPDATE bookings b
LEFT JOIN payments p ON p.booking_id = b.id AND p.status = 'PAID'
LEFT JOIN payments gp ON gp.booking_group_id = b.booking_group_id AND gp.status = 'PAID'
LEFT JOIN late_fee_payments lfp ON lfp.booking_id = b.id AND lfp.status = 'PAID'
SET b.paid_amount =
    CASE WHEN p.id IS NOT NULL OR gp.id IS NOT NULL THEN b.amount ELSE 0.00 END
    + CASE WHEN lfp.id IS NOT NULL THEN b.late_fee_amount ELSE 0.00 END;
