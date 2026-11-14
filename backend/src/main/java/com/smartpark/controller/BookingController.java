package com.smartpark.controller;

import com.smartpark.entity.Booking;
import com.smartpark.entity.BookingStatus;
import com.smartpark.entity.BillingPeriod;
import com.smartpark.entity.ParkingSlot;
import com.smartpark.security.CurrentUser;
import com.smartpark.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@Validated
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/availability")
    public List<AvailableSlotResponse> getAvailableSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime,
            @RequestParam(required = false) String vehicleType,
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) String locationQuery) {
        return bookingService.getAvailableSlots(
                startTime, endTime, vehicleType, latitude, longitude, radiusKm,
                locationQuery)
            .stream()
            .map(AvailableSlotResponse::from)
            .toList();
    }

    @GetMapping("/estimate")
    public ChargeEstimateResponse estimateBooking(
            @RequestParam @Positive Long slotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endTime,
            @RequestParam(defaultValue = "HOURLY") BillingPeriod billingPeriod) {
        var estimate = bookingService.estimateBooking(
            slotId, startTime, endTime, billingPeriod
        );
        return new ChargeEstimateResponse(
            estimate.durationMinutes(), estimate.hourlyRate(),
            estimate.billingPeriod(), estimate.billedRate(), estimate.amount()
        );
    }

    @GetMapping("/estimate/group")
    @PreAuthorize("hasRole('USER')")
    public BookingService.GroupChargeEstimate estimateBookingGroup(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestParam @Positive Long facilityId,
            @RequestParam @jakarta.validation.constraints.Size(min = 2, max = 10) List<@Positive Long> vehicleIds,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "HOURLY") BillingPeriod billingPeriod) {
        return bookingService.estimateBookingGroup(
            currentUser.id(), facilityId, vehicleIds, startTime, endTime, billingPeriod
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public BookingResponse createBooking(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CreateBookingRequest request) {
        return BookingResponse.from(bookingService.createBooking(
            currentUser.id(), request.vehicleId(), request.slotId(),
            request.startTime(), request.endTime(), request.billingPeriod()
        ));
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public BookingGroupResponse createBookingGroup(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody CreateBookingGroupRequest request) {
        return BookingGroupResponse.from(bookingService.createBookingGroup(
            currentUser.id(), request.facilityId(), request.vehicleIds(),
            request.startTime(), request.endTime(), request.billingPeriod()
        ));
    }

    @GetMapping("/groups/{groupId}")
    @PreAuthorize("hasRole('USER')")
    public BookingGroupResponse getBookingGroup(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long groupId) {
        return BookingGroupResponse.from(bookingService.getBookingGroup(groupId, currentUser.id()));
    }

    @PostMapping("/groups/{groupId}/cancel")
    @PreAuthorize("hasRole('USER')")
    public BookingGroupResponse cancelBookingGroup(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long groupId) {
        return BookingGroupResponse.from(bookingService.cancelBookingGroup(groupId, currentUser.id()));
    }

    @PostMapping("/groups/{groupId}/deposit")
    @PreAuthorize("hasRole('USER')")
    public BookingGroupResponse payGroupDeposit(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long groupId) {
        return BookingGroupResponse.from(
            bookingService.confirmGroupDeposit(groupId, currentUser.id())
        );
    }

    @GetMapping({"", "/my"})
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<BookingResponse> getMyBookings(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return bookingService.getBookingsForUser(currentUser.id()).stream()
            .map(BookingResponse::from)
            .toList();
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public BookingResponse getBooking(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long bookingId) {
        return BookingResponse.from(
            bookingService.getBookingForUser(bookingId, currentUser.id())
        );
    }

    @PostMapping("/{bookingId}/cancel")
    @PreAuthorize("hasRole('USER')")
    public BookingResponse cancelBooking(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long bookingId) {
        return BookingResponse.from(
            bookingService.cancelBooking(bookingId, currentUser.id())
        );
    }

    @PostMapping("/{bookingId}/deposit")
    @PreAuthorize("hasRole('USER')")
    public BookingResponse payDeposit(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long bookingId) {
        return BookingResponse.from(
            bookingService.confirmDeposit(bookingId, currentUser.id())
        );
    }

    @PostMapping("/{bookingId}/parked")
    @PreAuthorize("hasRole('USER')")
    public BookingResponse markParked(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long bookingId) {
        return BookingResponse.from(
            bookingService.markVehicleParked(bookingId, currentUser.id())
        );
    }

    @PostMapping("/{bookingId}/depart")
    @PreAuthorize("hasRole('USER')")
    public BookingResponse markDeparted(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long bookingId) {
        return BookingResponse.from(
            bookingService.markVehicleDeparted(bookingId, currentUser.id())
        );
    }

    @PostMapping("/{bookingId}/settle")
    @PreAuthorize("hasRole('USER')")
    public BookingResponse settleBalance(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long bookingId) {
        return BookingResponse.from(
            bookingService.settleBookingBalance(bookingId, currentUser.id())
        );
    }

    public record CreateBookingRequest(
        @NotNull @Positive Long vehicleId,
        @NotNull @Positive Long slotId,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime startTime,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime endTime,
        BillingPeriod billingPeriod
    ) {
        public CreateBookingRequest {
            if (billingPeriod == null) billingPeriod = BillingPeriod.HOURLY;
        }
    }

    public record CreateBookingGroupRequest(
        @NotNull @Positive Long facilityId,
        @NotNull @jakarta.validation.constraints.Size(min = 2, max = 10) List<@NotNull @Positive Long> vehicleIds,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
        BillingPeriod billingPeriod
    ) {
        public CreateBookingGroupRequest {
            if (billingPeriod == null) billingPeriod = BillingPeriod.HOURLY;
        }
    }

    public record BookingResponse(
        Long id,
        String bookingReference,
        Long userId,
        Long vehicleId,
        String vehicleNumber,
        Long slotId,
        String slotNumber,
        Long bookingGroupId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BookingStatus status,
        BigDecimal hourlyRate,
        BillingPeriod billingPeriod,
        BigDecimal billedRate,
        BigDecimal amount,
        LocalDateTime createdAt,
        LocalDateTime holdExpiresAt,
        LocalDateTime actualEntryTime,
        LocalDateTime actualExitTime,
        int lateHours,
        BigDecimal lateFeeAmount,
        BigDecimal depositAmount,
        BigDecimal paidAmount,
        BigDecimal depositRefundAmount,
        BigDecimal finalAmount,
        BigDecimal balanceDue,
        BigDecimal platformFeeAmount,
        BigDecimal ownerEarnings,
        LocalDateTime settlementPaidAt
    ) {
        static BookingResponse from(Booking booking) {
            return new BookingResponse(
                booking.getId(),
                booking.getBookingReference(),
                booking.getUser().getId(),
                booking.getVehicle().getId(),
                booking.getVehicle().getVehicleNumber(),
                booking.getParkingSlot().getId(),
                booking.getParkingSlot().getSlotNumber(),
                booking.getBookingGroup() == null ? null : booking.getBookingGroup().getId(),
                booking.getStartTime(),
                booking.getEndTime(),
                booking.getStatus(),
                booking.getHourlyRate(),
                booking.getBillingPeriod(),
                booking.getBilledRate(),
                booking.getAmount(),
                booking.getCreatedAt(),
                booking.getHoldExpiresAt(),
                booking.getActualEntryTime(),
                booking.getActualExitTime(),
                booking.getLateHours(),
                booking.getLateFeeAmount(),
                booking.getDepositAmount(),
                booking.getPaidAmount(),
                booking.getDepositRefundAmount(),
                booking.getFinalAmount(),
                booking.getBalanceDue(),
                booking.getPlatformFeeAmount(),
                booking.getOwnerEarnings(),
                booking.getSettlementPaidAt()
            );
        }

    }

    public record BookingGroupResponse(
        Long id,
        String groupReference,
        BigDecimal totalAmount,
        List<BookingResponse> bookings
    ) {
        static BookingGroupResponse from(BookingService.BookingGroupReservation reservation) {
            BigDecimal total = reservation.bookings().stream()
                .map(Booking::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            return new BookingGroupResponse(
                reservation.group().getId(),
                reservation.group().getGroupReference(),
                total,
                reservation.bookings().stream().map(BookingResponse::from).toList()
            );
        }
    }

    public record ChargeEstimateResponse(
        long durationMinutes,
        BigDecimal hourlyRate,
        BillingPeriod billingPeriod,
        BigDecimal billedRate,
        BigDecimal amount
    ) {
    }

    public record AvailableSlotResponse(
        Long id,
        String slotNumber,
        Integer floorNumber,
        String location,
        String zone,
        String vehicleType,
        BigDecimal hourlyPrice,
        BigDecimal dailyPrice,
        BigDecimal monthlyPrice,
        Long facilityId,
        String facilityName,
        String facilityAddress,
        BigDecimal latitude,
        BigDecimal longitude
    ) {
        static AvailableSlotResponse from(ParkingSlot slot) {
            return new AvailableSlotResponse(
                slot.getId(), slot.getSlotNumber(), slot.getFloorNumber(),
                slot.getLocation(), slot.getZone(), slot.getVehicleType(),
                slot.getHourlyPrice(), slot.getDailyPrice(), slot.getMonthlyPrice(),
                slot.getFacility().getId(), slot.getFacility().getName(),
                slot.getFacility().getAddress(), slot.getFacility().getLatitude(),
                slot.getFacility().getLongitude()
            );
        }
    }
}
