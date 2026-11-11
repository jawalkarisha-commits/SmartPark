package com.smartpark.service;

import com.smartpark.entity.Booking;
import com.smartpark.entity.BookingGroup;
import com.smartpark.entity.BookingStatus;
import com.smartpark.entity.BillingPeriod;
import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.ParkingFacility;
import com.smartpark.entity.User;
import com.smartpark.entity.Vehicle;
import com.smartpark.repository.BookingRepository;
import com.smartpark.repository.BookingGroupRepository;
import com.smartpark.repository.ParkingFacilityRepository;
import com.smartpark.repository.ParkingSlotRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.repository.VehicleRepository;
import com.smartpark.repository.PaymentRepository;
import com.smartpark.repository.SmartParkSettingsRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {

    private static final List<BookingStatus> BLOCKING_STATUSES = List.of(
        BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED,
        BookingStatus.ACTIVE, BookingStatus.OVERDUE
    );
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final PricingService pricingService;
    private final SmartParkSettingsRepository settingsRepository;
    private final RazorpayPaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final BookingGroupRepository bookingGroupRepository;
    private final ParkingFacilityRepository facilityRepository;
    private final ParkingSlotRepository parkingSlotRepository;

    public BookingService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            PricingService pricingService) {
        this(bookingRepository, userRepository, vehicleRepository, pricingService,
            null, null, null, null, null);
    }

    public BookingService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            PricingService pricingService,
            SmartParkSettingsRepository settingsRepository,
            RazorpayPaymentService paymentService,
            PaymentRepository paymentRepository,
            BookingGroupRepository bookingGroupRepository,
            ParkingFacilityRepository facilityRepository) {
        this(bookingRepository, userRepository, vehicleRepository, pricingService,
            settingsRepository, paymentService, paymentRepository,
            bookingGroupRepository, facilityRepository, null);
    }

    @Autowired
    public BookingService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            PricingService pricingService,
            SmartParkSettingsRepository settingsRepository,
            RazorpayPaymentService paymentService,
            PaymentRepository paymentRepository,
            BookingGroupRepository bookingGroupRepository,
            ParkingFacilityRepository facilityRepository,
            ParkingSlotRepository parkingSlotRepository) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.pricingService = pricingService;
        this.settingsRepository = settingsRepository;
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        this.bookingGroupRepository = bookingGroupRepository;
        this.facilityRepository = facilityRepository;
        this.parkingSlotRepository = parkingSlotRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingGroupReservation createBookingGroup(
            Long userId,
            Long facilityId,
            List<Long> vehicleIds,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BillingPeriod billingPeriod) {
        validateTimeWindow(startTime, endTime);
        if (billingPeriod == null || vehicleIds == null || vehicleIds.size() < 2
                || vehicleIds.size() > 10 || new HashSet<>(vehicleIds).size() != vehicleIds.size()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Choose 2 to 10 different vehicles and a billing period"
            );
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        var facility = facilityRepository.findById(facilityId)
            .filter(com.smartpark.entity.ParkingFacility::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facility not found"));

        BookingGroup group = bookingGroupRepository.save(new BookingGroup(user));
        List<Booking> reservations = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Long vehicleId : vehicleIds) {
            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .filter(item -> item.getOwner() != null
                    && item.getOwner().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Every selected vehicle must belong to this user"
                ));
            List<ParkingSlot> candidates = parkingSlotRepository.findAvailableSlots(
                vehicle.getVehicleType(), BLOCKING_STATUSES, startTime, endTime
            ).stream()
                .filter(slot -> slot.getFacility().getId().equals(facilityId))
                .toList();
            ParkingSlot allocated = null;
            for (ParkingSlot candidate : candidates) {
                if (reservations.stream().anyMatch(
                        booking -> booking.getParkingSlot().getId().equals(candidate.getId()))) {
                    continue;
                }
                ParkingSlot locked = bookingRepository.lockSlotById(candidate.getId())
                    .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "A parking space changed during allocation"
                    ));
                if (locked.isAvailable()
                        && (locked.getVehicleType() == null
                            || locked.getVehicleType().equalsIgnoreCase(vehicle.getVehicleType()))
                        && !bookingRepository.existsOverlappingBooking(
                            locked.getId(), BLOCKING_STATUSES, startTime, endTime, now)) {
                    allocated = locked;
                    break;
                }
            }
            if (allocated == null) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Not enough compatible spaces are available for every selected vehicle"
                );
            }
            PricingService.ChargeEstimate estimate =
                pricingService.estimate(allocated, startTime, endTime, billingPeriod);
            Booking booking = new Booking(
                user, vehicle, allocated, startTime, endTime, billingPeriod,
                estimate.hourlyRate(), estimate.billedRate(), estimate.amount()
            );
            booking.assignToGroup(group);
            booking.beginPaymentHold(now);
            reservations.add(bookingRepository.save(booking));
        }
        return new BookingGroupReservation(group, reservations);
    }

    @Transactional(readOnly = true)
    public GroupChargeEstimate estimateBookingGroup(
            Long userId, Long facilityId, List<Long> vehicleIds,
            LocalDateTime startTime, LocalDateTime endTime, BillingPeriod billingPeriod) {
        validateTimeWindow(startTime, endTime);
        if (billingPeriod == null || vehicleIds == null || vehicleIds.size() < 2
                || vehicleIds.size() > 10 || new HashSet<>(vehicleIds).size() != vehicleIds.size()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Choose 2 to 10 different vehicles and a billing period"
            );
        }
        facilityRepository.findById(facilityId)
            .filter(com.smartpark.entity.ParkingFacility::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Facility not found"));
        List<Long> allocatedSlotIds = new ArrayList<>();
        List<GroupVehicleEstimate> vehicles = new ArrayList<>();
        for (Long vehicleId : vehicleIds) {
            Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .filter(item -> item.getOwner() != null
                    && item.getOwner().getId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Every selected vehicle must belong to this user"
                ));
            ParkingSlot slot = parkingSlotRepository.findAvailableSlots(
                    vehicle.getVehicleType(), BLOCKING_STATUSES, startTime, endTime)
                .stream()
                .filter(candidate -> candidate.getFacility().getId().equals(facilityId))
                .filter(candidate -> !allocatedSlotIds.contains(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Not enough compatible spaces are available for every selected vehicle"
                ));
            allocatedSlotIds.add(slot.getId());
            PricingService.ChargeEstimate estimate =
                pricingService.estimate(slot, startTime, endTime, billingPeriod);
            vehicles.add(new GroupVehicleEstimate(
                vehicle.getId(), vehicle.getVehicleNumber(), slot.getId(),
                slot.getSlotNumber(), estimate.amount()
            ));
        }
        BigDecimal total = vehicles.stream()
            .map(GroupVehicleEstimate::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new GroupChargeEstimate(total, vehicles);
    }

    @Transactional(readOnly = true)
    public BookingGroupReservation getBookingGroup(Long groupId, Long userId) {
        BookingGroup group = bookingGroupRepository.findByIdAndUser_Id(groupId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking group not found"));
        return new BookingGroupReservation(
            group, bookingRepository.findAllByBookingGroup_IdOrderByIdAsc(groupId)
        );
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(
            Long userId,
            Long vehicleId,
            Long slotId,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return createBooking(userId, vehicleId, slotId, startTime, endTime, BillingPeriod.HOURLY);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(
            Long userId,
            Long vehicleId,
            Long slotId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BillingPeriod billingPeriod) {
        validateTimeWindow(startTime, endTime);
        if (billingPeriod == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Billing period is required");
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found"
            ));
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Vehicle not found"
            ));
        if (vehicle.getOwner() == null
                || !vehicle.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Vehicle does not belong to this user"
            );
        }
        ParkingSlot slot = bookingRepository.lockSlotById(slotId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Parking slot not found"
            ));

        if (!slot.isAvailable()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Parking slot is unavailable"
            );
        }
        if (slot.getVehicleType() != null
                && !slot.getVehicleType().equalsIgnoreCase(vehicle.getVehicleType())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Parking slot does not support this vehicle type"
            );
        }
        LocalDateTime now = LocalDateTime.now();
        if (bookingRepository.existsOverlappingBooking(
                slotId, BLOCKING_STATUSES, startTime, endTime, now)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Parking slot is already booked for this time"
            );
        }

        PricingService.ChargeEstimate estimate =
            pricingService.estimate(slot, startTime, endTime, billingPeriod);
        Booking booking = new Booking(
                user, vehicle, slot, startTime, endTime, billingPeriod,
                estimate.hourlyRate(), estimate.billedRate(), estimate.amount()
            );
        booking.beginPaymentHold(now);
        return bookingRepository.save(booking);
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return bookingRepository.findAllForUser(userId);
    }

    @Transactional(readOnly = true)
    public Booking getBookingForUser(Long bookingId, Long userId) {
        return bookingRepository.findByIdAndUser_Id(bookingId, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found"
            ));
    }

    @Transactional(readOnly = true)
    public List<ParkingSlot> getAvailableSlots(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String vehicleType) {
        validateTimeWindow(startTime, endTime);
        return parkingSlotRepository.findAvailableSlots(
            vehicleType, BLOCKING_STATUSES,
            startTime, endTime
        );
    }

    @Transactional(readOnly = true)
    public List<ParkingSlot> getAvailableSlots(
            LocalDateTime startTime, LocalDateTime endTime, String vehicleType,
            BigDecimal latitude, BigDecimal longitude, Double radiusKm) {
        return getAvailableSlots(
            startTime, endTime, vehicleType, latitude, longitude, radiusKm, null
        );
    }

    @Transactional(readOnly = true)
    public List<ParkingSlot> getAvailableSlots(
            LocalDateTime startTime, LocalDateTime endTime, String vehicleType,
            BigDecimal latitude, BigDecimal longitude, Double radiusKm,
            String locationQuery) {
        List<ParkingSlot> available = getAvailableSlots(startTime, endTime, vehicleType);
        String normalizedQuery = locationQuery == null || locationQuery.isBlank()
            ? null : locationQuery.strip().toLowerCase(java.util.Locale.ROOT);
        if (latitude == null && longitude == null && radiusKm == null) {
            if (normalizedQuery == null) return available;
            return available.stream()
                .filter(slot -> matchesLocationQuery(slot.getFacility(), normalizedQuery))
                .toList();
        }
        if (latitude == null || longitude == null || radiusKm == null
                || latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0
                || radiusKm <= 0 || radiusKm > 200) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Provide valid latitude, longitude, and a radius from 0 to 200 km"
            );
        }
        return available.stream()
            .filter(slot -> {
                ParkingFacility facility = slot.getFacility();
                if (facility.getLatitude() == null || facility.getLongitude() == null) {
                    return normalizedQuery != null
                        && matchesLocationQuery(facility, normalizedQuery);
                }
                return distanceKm(
                    latitude.doubleValue(), longitude.doubleValue(),
                    facility.getLatitude().doubleValue(),
                    facility.getLongitude().doubleValue()
                ) <= radiusKm;
            })
            .toList();
    }

    private static boolean matchesLocationQuery(
            ParkingFacility facility, String normalizedQuery) {
        return facility.getName().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery)
            || facility.getAddress().toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery);
    }

    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Transactional(readOnly = true)
    public PricingService.ChargeEstimate estimateBooking(
            Long slotId,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        return estimateBooking(slotId, startTime, endTime, BillingPeriod.HOURLY);
    }

    @Transactional(readOnly = true)
    public PricingService.ChargeEstimate estimateBooking(
            Long slotId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BillingPeriod billingPeriod) {
        validateTimeWindow(startTime, endTime);
        if (billingPeriod == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Billing period is required");
        }
        ParkingSlot slot = bookingRepository.findSlotById(slotId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Parking slot not found"
            ));
        if (!slot.isAvailable()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Parking slot is unavailable"
            );
        }
        if (bookingRepository.existsOverlappingBooking(
                slotId, BLOCKING_STATUSES, startTime, endTime, LocalDateTime.now())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Parking slot is already booked for this time"
            );
        }
        return pricingService.estimate(slot, startTime, endTime, billingPeriod);
    }

    @Transactional
    public int expirePaymentHolds(LocalDateTime now) {
        List<Booking> expired = bookingRepository.findExpiredPaymentHolds(now);
        expired.forEach(booking -> booking.expirePaymentHold(now));
        return expired.size();
    }

    @Transactional
    public int markOverdueSessions(LocalDateTime now) {
        List<Booking> overdue = bookingRepository.findActiveOverdueBookings(now);
        overdue.forEach(Booking::markOverdue);
        return overdue.size();
    }

    @Transactional
    public Booking startSession(Long bookingId, Long ownerId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found"
            ));
        if (booking.getParkingSlot().getFacility().getOwner() == null
                || !booking.getParkingSlot().getFacility().getOwner().getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        LocalDateTime now = LocalDateTime.now();
        if (booking.getStatus() != BookingStatus.CONFIRMED
                || now.isBefore(booking.getStartTime())
                || !now.isBefore(booking.getEndTime())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only a paid reservation within its scheduled time can be started"
            );
        }
        if (paymentRepository != null && paymentRepository.findByBooking_Id(bookingId)
                .filter(payment -> "PAID".equals(payment.getStatus()))
                .isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "A verified payment is required before vehicle entry"
            );
        }
        booking.start(now);
        return booking;
    }

    @Transactional
    public Booking completeSession(Long bookingId, Long ownerId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found"
            ));
        if (booking.getParkingSlot().getFacility().getOwner() == null
                || !booking.getParkingSlot().getFacility().getOwner().getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        try {
            booking.complete(LocalDateTime.now());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, exception.getMessage(), exception
            );
        }
        return booking;
    }

    @Transactional
    public Booking cancelByOwner(Long bookingId, Long ownerId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found"
            ));
        if (booking.getParkingSlot().getFacility().getOwner() == null
                || !booking.getParkingSlot().getFacility().getOwner().getId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only future confirmed bookings can be cancelled by the owner"
            );
        }
        if (paymentService != null) {
            paymentService.refundForCancellation(bookingId, BigDecimal.valueOf(100));
        }
        booking.cancel();
        return booking;
    }

    @Transactional
    public Booking cancelBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found"
            ));

        if (!booking.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Booking does not belong to this user"
            );
        }
        if (booking.getBookingGroup() != null) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Cancel this reservation through its booking group"
            );
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED
                && booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only pending or confirmed reservations can be cancelled"
            );
        }
        var settings = settingsRepository == null
            ? null : settingsRepository.findById(1).orElse(null);
        if (settings != null && !settings.isCancellationEnabled()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Booking cancellation is currently disabled"
            );
        }
        LocalDateTime cutoff = LocalDateTime.now().plusMinutes(
            settings == null ? 0 : settings.getCancellationCutoffMinutes()
        );
        if (booking.getStatus() == BookingStatus.CONFIRMED
                && !cutoff.isBefore(booking.getStartTime())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "A booking cannot be cancelled after it starts"
            );
        }

        booking.cancel();
        if (paymentService != null) {
            paymentService.refundForCancellation(
                bookingId, settings == null
                    ? BigDecimal.valueOf(100) : settings.getRefundPercentage()
            );
        }
        return booking;
    }

    @Transactional
    public BookingGroupReservation cancelBookingGroup(Long groupId, Long userId) {
        BookingGroup group = bookingGroupRepository.findByIdAndUser_Id(groupId, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking group not found"
            ));
        List<Booking> bookings = bookingRepository.findAllByBookingGroup_IdOrderByIdAsc(groupId);
        if (bookings.isEmpty() || bookings.stream().anyMatch(booking ->
                booking.getStatus() != BookingStatus.PENDING_PAYMENT
                    && booking.getStatus() != BookingStatus.CONFIRMED)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only unpaid or future booking groups can be cancelled"
            );
        }
        var settings = settingsRepository == null
            ? null : settingsRepository.findById(1).orElse(null);
        if (settings != null && !settings.isCancellationEnabled()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Booking cancellation is currently disabled"
            );
        }
        if (bookings.stream().anyMatch(booking ->
                booking.getStatus() == BookingStatus.CONFIRMED
                    && !LocalDateTime.now().plusMinutes(
                        settings == null ? 0 : settings.getCancellationCutoffMinutes()
                    ).isBefore(booking.getStartTime()))) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "A booking group cannot be cancelled after its cancellation cutoff"
            );
        }
        if (paymentService != null) {
            paymentService.refundGroupForCancellation(
                groupId, settings == null ? BigDecimal.valueOf(100) : settings.getRefundPercentage()
            );
        }
        bookings.forEach(Booking::cancel);
        return new BookingGroupReservation(group, bookings);
    }

    public record BookingGroupReservation(BookingGroup group, List<Booking> bookings) {
    }

    public record GroupVehicleEstimate(
        Long vehicleId, String vehicleNumber, Long slotId, String slotNumber, BigDecimal amount
    ) {
    }

    public record GroupChargeEstimate(BigDecimal totalAmount, List<GroupVehicleEstimate> vehicles) {
    }

    private static void validateTimeWindow(
            LocalDateTime startTime,
            LocalDateTime endTime) {
        if (startTime == null || endTime == null
                || !startTime.isAfter(LocalDateTime.now())
                || !endTime.isAfter(startTime)
                || startTime.getSecond() != 0
                || startTime.getNano() != 0
                || endTime.getSecond() != 0
                || endTime.getNano() != 0
                || ChronoUnit.MINUTES.between(startTime, endTime) < 1) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Start time must be in the future, times must be minute-precision, "
                    + "and end time must be at least one minute after start time"
            );
        }
    }
}
