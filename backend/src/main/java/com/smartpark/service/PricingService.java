package com.smartpark.service;

import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.BillingPeriod;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PricingService {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);

    public ChargeEstimate estimate(
            ParkingSlot slot,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return estimate(slot, startTime, endTime, BillingPeriod.HOURLY);
    }

    public ChargeEstimate estimate(
            ParkingSlot slot,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BillingPeriod billingPeriod) {
        BigDecimal hourlyRate = slot.getHourlyPrice();
        BigDecimal billedRate = switch (billingPeriod) {
            case HOURLY -> hourlyRate;
            case DAILY -> slot.getDailyPrice();
            case MONTHLY -> slot.getMonthlyPrice();
        };
        if (billedRate == null || billedRate.signum() <= 0) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Parking slot has no valid rate for this billing period"
            );
        }

        long durationMinutes = Duration.between(startTime, endTime).toMinutes();
        BigDecimal minutesPerUnit = switch (billingPeriod) {
            case HOURLY -> MINUTES_PER_HOUR;
            case DAILY -> BigDecimal.valueOf(24 * 60L);
            case MONTHLY -> BigDecimal.valueOf(30 * 24 * 60L);
        };
        BigDecimal amount = billedRate.multiply(BigDecimal.valueOf(durationMinutes))
            .divide(minutesPerUnit, 2, RoundingMode.HALF_UP);

        return new ChargeEstimate(durationMinutes, hourlyRate, billingPeriod, billedRate, amount);
    }

    public record ChargeEstimate(
        long durationMinutes,
        BigDecimal hourlyRate,
        BillingPeriod billingPeriod,
        BigDecimal billedRate,
        BigDecimal amount) {
    }
}
