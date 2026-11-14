package com.smartpark.service;

import java.time.LocalDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingMaintenanceJob {

    private final BookingService bookingService;

    public BookingMaintenanceJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(fixedDelay = 30_000)
    public void expirePaymentHoldsAndMarkOverdueSessions() {
        LocalDateTime now = LocalDateTime.now();
        bookingService.expirePaymentHolds(now);
        bookingService.markOverdueSessions(now);
    }
}
