package com.smartpark.service;

import com.smartpark.entity.Booking;
import com.smartpark.entity.BookingStatus;
import com.smartpark.entity.ParkingSlotStatus;
import com.smartpark.entity.User;
import com.smartpark.entity.Vehicle;
import com.smartpark.repository.BookingRepository;
import com.smartpark.repository.PaymentRepository;
import com.smartpark.repository.SmartParkSettingsRepository;
import com.smartpark.repository.ParkingSlotRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.repository.VehicleRepository;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminService {

    private static final List<BookingStatus> BLOCKING_STATUSES =
        List.of(BookingStatus.CONFIRMED, BookingStatus.ACTIVE, BookingStatus.OVERDUE);

    private final BookingRepository bookingRepository;
    private final ParkingSlotRepository slotRepository;
    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final PaymentRepository paymentRepository;
    private final SmartParkSettingsRepository settingsRepository;
    private final RazorpayPaymentService paymentService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final OwnerVerificationService ownerVerificationService;

    public AdminService(
            BookingRepository bookingRepository,
            ParkingSlotRepository slotRepository,
            UserRepository userRepository,
            VehicleRepository vehicleRepository,
            PaymentRepository paymentRepository,
            SmartParkSettingsRepository settingsRepository,
            RazorpayPaymentService paymentService,
            NamedParameterJdbcTemplate jdbcTemplate,
            OwnerVerificationService ownerVerificationService) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.userRepository = userRepository;
        this.vehicleRepository = vehicleRepository;
        this.paymentRepository = paymentRepository;
        this.settingsRepository = settingsRepository;
        this.paymentService = paymentService;
        this.jdbcTemplate = jdbcTemplate;
        this.ownerVerificationService = ownerVerificationService;
    }

    @Transactional(readOnly = true)
    public DashboardMetrics getDashboard() {
        LocalDateTime now = LocalDateTime.now();
        long totalSlots = slotRepository.count();
        long maintenance = slotRepository.countByStatus(ParkingSlotStatus.MAINTENANCE);
        long occupied = bookingRepository.countSlotsBookedAt(
            List.of(BookingStatus.ACTIVE), now
        );
        long currentlyReserved = bookingRepository.countAvailableSlotsBookedAt(
            List.of(BookingStatus.CONFIRMED), now
        );
        long available = Math.max(
            0,
            slotRepository.countByStatus(ParkingSlotStatus.AVAILABLE)
                - bookingRepository.countAvailableSlotsBookedAt(BLOCKING_STATUSES, now)
        );
        long futureReserved = bookingRepository.countFutureReservedSlots(
            BookingStatus.CONFIRMED, now
        );
        BigDecimal bookingValue = bookingRepository.sumAmountByStatuses(
            EnumSet.of(
                BookingStatus.CONFIRMED,
                BookingStatus.ACTIVE,
                BookingStatus.COMPLETED
            )
        );
        BigDecimal completedValue = bookingRepository.sumAmountByStatuses(
            List.of(BookingStatus.COMPLETED)
        );

        return new DashboardMetrics(
            totalSlots,
            available,
            occupied,
            futureReserved + currentlyReserved,
            maintenance,
            userRepository.countRegisteredUsers(),
            bookingRepository.count(),
            bookingRepository.countByStatus(BookingStatus.CONFIRMED),
            bookingRepository.countByStatus(BookingStatus.ACTIVE),
            bookingRepository.countByStatus(BookingStatus.COMPLETED),
            bookingRepository.countByStatus(BookingStatus.CANCELLED),
            bookingValue,
            completedValue,
            bookingRepository.sumSettledPlatformFees()
        );
    }

    @Transactional(readOnly = true)
    public Page<Booking> searchBookings(
            BookingStatus status,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "End date must be after start date"
            );
        }
        return bookingRepository.searchForAdmin(status, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Booking getBooking(Long bookingId) {
        return bookingRepository.findDetailedById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found"
            ));
    }

    @Transactional
    public Booking startBooking(Long bookingId) {
        Booking booking = getBookingForUpdate(bookingId);
        LocalDateTime now = LocalDateTime.now();
        if (booking.getStatus() != BookingStatus.CONFIRMED
                || now.isBefore(booking.getStartTime())
                || !now.isBefore(booking.getEndTime())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only a confirmed booking within its scheduled time can be started"
            );
        }
        if (!booking.isDepositPaid()
                && paymentRepository.findByBooking_Id(bookingId)
                    .filter(payment -> "PAID".equals(payment.getStatus()))
                    .isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "A paid reservation deposit is required before vehicle entry"
            );
        }
        booking.start(now);
        return booking;
    }

    @Transactional
    public Booking completeBooking(Long bookingId) {
        Booking booking = getBookingForUpdate(bookingId);
        if (booking.getStatus() != BookingStatus.ACTIVE
                && booking.getStatus() != BookingStatus.OVERDUE) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only active bookings can be completed"
            );
        }
        booking.complete(LocalDateTime.now());
        return booking;
    }

    @Transactional
    public Booking cancelBooking(Long bookingId) {
        Booking booking = getBookingForUpdate(bookingId);
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Only confirmed bookings can be cancelled"
            );
        }
        var settings = settingsRepository.findById(1).orElse(null);
        BigDecimal refundPercentage = settings == null
            ? BigDecimal.valueOf(100) : settings.getRefundPercentage();
        if (booking.getPaidAmount().signum() > 0) {
            if (paymentService != null && paymentRepository.findByBooking_Id(bookingId)
                    .filter(payment -> "PAID".equals(payment.getStatus()))
                    .isPresent()) {
                paymentService.refundForCancellation(bookingId, refundPercentage);
            }
            booking.refundPaidAmount(booking.getPaidAmount()
                .multiply(refundPercentage)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
        }
        booking.cancel();
        return booking;
    }

    @Transactional(readOnly = true)
    public Page<User> searchUsers(String search, Pageable pageable) {
        String normalized = search == null || search.isBlank() ? null : search.trim();
        return userRepository.searchUsers(normalized, pageable);
    }

    @Transactional(readOnly = true)
    public List<User> getUnverifiedOwnerAccounts() {
        return userRepository.findUnverifiedOwnerAccounts();
    }

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found"
            ));
    }

    @Transactional(readOnly = true)
    public List<Booking> getUserBookings(Long userId) {
        getUser(userId);
        return bookingRepository.findAllForUser(userId);
    }

    @Transactional
    public User updateUserStatus(Long userId, Long adminId, boolean enabled) {
        User user = getUser(userId);
        if (userId.equals(adminId) && !enabled) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "You cannot deactivate your own account"
            );
        }
        user.setEnabled(enabled);
        return user;
    }

    @Transactional
    public void deleteUser(Long userId, Long adminId) {
        User user = getUser(userId);
        if (userId.equals(adminId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "You cannot delete your own account"
            );
        }
        if ("ADMIN".equals(user.getRole()) && user.isEnabled()
                && userRepository.countByRoleAndEnabledTrue("ADMIN") <= 1) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "The last active administrator cannot be deleted"
            );
        }

        Long targetId = user.getId();
        String storageKey = jdbcTemplate.query(
            "SELECT document_storage_key FROM owner_verifications "
                + "WHERE owner_user_id = :userId",
            new MapSqlParameterSource("userId", targetId),
            resultSet -> resultSet.next() ? resultSet.getString(1) : null
        );
        List<Long> facilityIds = queryIds(
            "SELECT id FROM parking_facilities WHERE owner_user_id = :userId",
            Map.of("userId", targetId)
        );
        List<Long> vehicleIds = queryIds(
            "SELECT id FROM vehicles WHERE user_id = :userId",
            Map.of("userId", targetId)
        );
        List<Long> slotIds = facilityIds.isEmpty()
            ? List.of()
            : queryIds(
                "SELECT id FROM parking_slots WHERE facility_id IN (:facilityIds)",
                Map.of("facilityIds", facilityIds)
            );

        Set<Long> groupIds = new LinkedHashSet<>(queryIds(
            "SELECT id FROM booking_groups WHERE user_id = :userId",
            Map.of("userId", targetId)
        ));
        Set<Long> bookingIds = new LinkedHashSet<>(queryIds(
            "SELECT id FROM bookings WHERE user_id = :userId",
            Map.of("userId", targetId)
        ));
        if (!vehicleIds.isEmpty()) {
            bookingIds.addAll(queryIds(
                "SELECT id FROM bookings WHERE vehicle_id IN (:vehicleIds)",
                Map.of("vehicleIds", vehicleIds)
            ));
        }
        if (!slotIds.isEmpty()) {
            bookingIds.addAll(queryIds(
                "SELECT id FROM bookings WHERE parking_slot_id IN (:slotIds)",
                Map.of("slotIds", slotIds)
            ));
        }
        if (!bookingIds.isEmpty()) {
            groupIds.addAll(queryIds(
                "SELECT DISTINCT booking_group_id FROM bookings "
                    + "WHERE id IN (:ids) AND booking_group_id IS NOT NULL",
                Map.of("ids", bookingIds)
            ));
        }
        if (!groupIds.isEmpty()) {
            bookingIds.addAll(queryIds(
                "SELECT id FROM bookings WHERE booking_group_id IN (:groupIds)",
                Map.of("groupIds", groupIds)
            ));
        }

        Set<Long> paymentIds = new LinkedHashSet<>();
        if (!bookingIds.isEmpty()) {
            paymentIds.addAll(queryIds(
                "SELECT id FROM payments WHERE booking_id IN (:bookingIds)",
                Map.of("bookingIds", bookingIds)
            ));
        }
        if (!groupIds.isEmpty()) {
            paymentIds.addAll(queryIds(
                "SELECT id FROM payments WHERE booking_group_id IN (:groupIds)",
                Map.of("groupIds", groupIds)
            ));
        }

        deleteByIds("payment_refunds", "payment_id", paymentIds);
        deleteByIds("late_fee_payments", "booking_id", bookingIds);
        deleteByIds("payments", "id", paymentIds);
        deleteByIds("bookings", "id", bookingIds);
        deleteByIds("booking_groups", "id", groupIds);
        deleteByIds("parking_slots", "id", new LinkedHashSet<>(slotIds));
        jdbcTemplate.update(
            "DELETE FROM parking_facilities WHERE owner_user_id = :userId",
            Map.of("userId", targetId)
        );
        deleteByIds("vehicles", "id", new LinkedHashSet<>(vehicleIds));
        jdbcTemplate.update(
            "DELETE FROM password_reset_tokens WHERE user_id = :userId",
            Map.of("userId", targetId)
        );
        jdbcTemplate.update(
            "DELETE FROM owner_verifications WHERE reviewed_by_user_id = :userId",
            Map.of("userId", targetId)
        );
        jdbcTemplate.update(
            "DELETE FROM owner_verifications WHERE owner_user_id = :userId",
            Map.of("userId", targetId)
        );
        jdbcTemplate.update(
            "DELETE FROM customer_registrations WHERE email = :email",
            Map.of("email", user.getEmail())
        );
        userRepository.delete(user);
        userRepository.flush();

        if (storageKey != null) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        ownerVerificationService.deleteDocumentFile(targetId, storageKey);
                    }
                }
            );
        }
    }

    private List<Long> queryIds(String sql, Map<String, ?> parameters) {
        return jdbcTemplate.queryForList(
            sql, new MapSqlParameterSource(parameters), Long.class
        );
    }

    private void deleteByIds(String table, String column, Set<Long> ids) {
        if (!ids.isEmpty()) {
            jdbcTemplate.update(
                "DELETE FROM " + table + " WHERE " + column + " IN (:ids)",
                Map.of("ids", new ArrayList<>(ids))
            );
        }
    }

    @Transactional(readOnly = true)
    public List<Vehicle> getUnassignedVehicles() {
        return vehicleRepository.findAllByOwnerIsNullOrderByIdAsc();
    }

    @Transactional
    public Vehicle assignVehicleToUser(Long vehicleId, Long userId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Vehicle not found"
            ));
        if (vehicle.getOwner() != null) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Vehicle already has an assigned owner"
            );
        }
        User owner = getUser(userId);
        vehicle.setOwner(owner);
        return vehicle;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(
            LocalDate from,
            LocalDate to,
            GroupBy groupBy) {
        if (from == null || to == null || !to.isAfter(from)
                || to.isAfter(from.plusYears(1))) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Provide a date range of up to one year with end date after start date"
            );
        }
        List<Booking> bookings = bookingRepository.findAnalyticsBookings(
            from.atStartOfDay(), to.atStartOfDay(),
            List.of(
                BookingStatus.CONFIRMED,
                BookingStatus.ACTIVE,
                BookingStatus.COMPLETED
            )
        );

        Map<LocalDate, RevenueAccumulator> points = new LinkedHashMap<>();
        Map<Long, SlotAccumulator> slots = new HashMap<>();
        Map<String, Long> vehicleTypes = new HashMap<>();
        for (Booking booking : bookings) {
            LocalDate bucket = bucketDate(booking.getCreatedAt().toLocalDate(), groupBy);
            RevenueAccumulator point = points.computeIfAbsent(
                bucket, ignored -> new RevenueAccumulator()
            );
            point.count++;
            point.amount = point.amount.add(booking.getAmount());

            SlotAccumulator slot = slots.computeIfAbsent(
                booking.getParkingSlot().getId(),
                ignored -> new SlotAccumulator(booking.getParkingSlot().getSlotNumber())
            );
            slot.count++;
            String vehicleType = booking.getVehicle().getVehicleType();
            vehicleTypes.merge(
                vehicleType == null ? "Unspecified" : vehicleType,
                1L,
                Long::sum
            );
        }

        List<RevenuePoint> revenueTrend = points.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new RevenuePoint(
                entry.getKey(), entry.getValue().count, entry.getValue().amount
            ))
            .toList();
        List<PopularSlot> popularSlots = slots.entrySet().stream()
            .map(entry -> new PopularSlot(
                entry.getKey(), entry.getValue().slotNumber, entry.getValue().count
            ))
            .sorted(Comparator.comparingLong(PopularSlot::bookings).reversed())
            .limit(10)
            .toList();

        long occupiedSlots = bookingRepository.countSlotsBookedAt(
            List.of(BookingStatus.ACTIVE), LocalDateTime.now()
        );
        long confirmed = bookingRepository.countByStatus(BookingStatus.CONFIRMED);
        return new AnalyticsResponse(
            from, to, groupBy, revenueTrend, popularSlots, vehicleTypes,
            occupiedSlots, confirmed
        );
    }

    private Booking getBookingForUpdate(Long bookingId) {
        return bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found"
            ));
    }

    private static LocalDate bucketDate(LocalDate date, GroupBy groupBy) {
        return switch (groupBy) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    public enum GroupBy {
        DAY,
        WEEK,
        MONTH
    }

    public record DashboardMetrics(
        long totalSlots,
        long availableSlots,
        long occupiedSlots,
        long reservedSlots,
        long maintenanceSlots,
        long totalUsers,
        long totalBookings,
        long confirmedBookings,
        long activeBookings,
        long completedBookings,
        long cancelledBookings,
        BigDecimal bookedValue,
        BigDecimal completedBookingValue,
        BigDecimal platformFeeTotal
    ) {
    }

    public record RevenuePoint(
        LocalDate date,
        long bookings,
        BigDecimal amount
    ) {
    }

    public record PopularSlot(
        Long slotId,
        String slotNumber,
        long bookings
    ) {
    }

    public record AnalyticsResponse(
        LocalDate from,
        LocalDate to,
        GroupBy groupBy,
        List<RevenuePoint> revenueTrend,
        List<PopularSlot> mostBookedSlots,
        Map<String, Long> vehicleTypeDistribution,
        long currentOccupancy,
        long confirmedBookings
    ) {
    }

    private static final class RevenueAccumulator {
        private long count;
        private BigDecimal amount = BigDecimal.ZERO;
    }

    private static final class SlotAccumulator {
        private final String slotNumber;
        private long count;

        private SlotAccumulator(String slotNumber) {
            this.slotNumber = slotNumber;
        }
    }
}
