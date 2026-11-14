package com.smartpark;

import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.BillingPeriod;
import com.smartpark.service.PricingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PricingServiceTest {

    private final PricingService pricingService = new PricingService();

    @Test
    void proratesCommonDurationsUsingHourlyRate() {
        ParkingSlot slot = new ParkingSlot();
        slot.setHourlyPrice(new BigDecimal("12.00"));
        LocalDateTime start = LocalDateTime.now()
            .plusDays(1)
            .truncatedTo(ChronoUnit.MINUTES);

        assertEstimate(slot, start, 30, "6.00");
        assertEstimate(slot, start, 45, "9.00");
        assertEstimate(slot, start, 90, "18.00");
        assertEstimate(slot, start, 120, "24.00");
    }

    @Test
    void roundsFinalChargeToTwoDecimalPlacesHalfUp() {
        ParkingSlot slot = new ParkingSlot();
        slot.setHourlyPrice(new BigDecimal("10.00"));
        LocalDateTime start = LocalDateTime.now()
            .plusDays(1)
            .truncatedTo(ChronoUnit.MINUTES);

        assertEstimate(slot, start, 1, "0.17");
    }

    @Test
    void proratesDailyAndThirtyDayMonthlyRatesByExactMinutes() {
        ParkingSlot slot = new ParkingSlot();
        slot.setHourlyPrice(new BigDecimal("60.00"));
        slot.setDailyPrice(new BigDecimal("240.00"));
        slot.setMonthlyPrice(new BigDecimal("3000.00"));
        LocalDateTime start = LocalDateTime.now()
            .plusDays(1)
            .truncatedTo(ChronoUnit.MINUTES);

        assertEquals(
            new BigDecimal("5.00"),
            pricingService.estimate(
                slot, start, start.plusMinutes(30), BillingPeriod.DAILY
            ).amount()
        );
        assertEquals(
            new BigDecimal("3000.00"),
            pricingService.estimate(
                slot, start, start.plusDays(30), BillingPeriod.MONTHLY
            ).amount()
        );
    }

    private void assertEstimate(
            ParkingSlot slot,
            LocalDateTime start,
            int durationMinutes,
            String expectedAmount) {
        PricingService.ChargeEstimate estimate = pricingService.estimate(
            slot, start, start.plusMinutes(durationMinutes)
        );

        assertEquals(durationMinutes, estimate.durationMinutes());
        assertEquals(new BigDecimal(expectedAmount), estimate.amount());
    }
}
