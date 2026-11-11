package com.smartpark.repository;

import com.smartpark.entity.Booking;
import com.smartpark.entity.BookingStatus;
import com.smartpark.entity.ParkingSlot;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    boolean existsByVehicle_Id(Long vehicleId);

    boolean existsByParkingSlot_Id(Long slotId);

    @Query("""
        select b
        from Booking b
        where b.status = com.smartpark.entity.BookingStatus.PENDING_PAYMENT
          and b.holdExpiresAt <= :now
        """)
    List<Booking> findExpiredPaymentHolds(@Param("now") LocalDateTime now);

    @Query("""
        select b
        from Booking b
        where b.status = com.smartpark.entity.BookingStatus.ACTIVE
          and b.endTime < :now
        """)
    List<Booking> findActiveOverdueBookings(@Param("now") LocalDateTime now);

    @Query("""
        select count(b) > 0
        from Booking b
        where b.parkingSlot.id = :slotId
          and b.status in :statuses
          and (b.status <> com.smartpark.entity.BookingStatus.PENDING_PAYMENT
               or b.holdExpiresAt > :now)
          and b.startTime < :endTime
          and b.endTime > :startTime
        """)
    boolean existsOverlappingBooking(
        @Param("slotId") Long slotId,
        @Param("statuses") Collection<BookingStatus> statuses,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        @Param("now") LocalDateTime now);

    @Query("""
        select b
        from Booking b
        where b.user.id = :userId
        order by b.startTime desc
        """)
    @EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot", "bookingGroup"})
    List<Booking> findAllForUser(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot", "bookingGroup"})
    List<Booking> findTop100ByParkingSlot_Facility_IdOrderByCreatedAtDesc(Long facilityId);

    @EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot", "bookingGroup"})
    List<Booking> findAllByBookingGroup_IdOrderByIdAsc(Long bookingGroupId);

    @EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot", "bookingGroup"})
    Optional<Booking> findByIdAndUser_Id(Long id, Long userId);

    @EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot", "bookingGroup"})
    @Query("select b from Booking b where b.id = :bookingId")
    Optional<Booking> findDetailedById(@Param("bookingId") Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot", "bookingGroup"})
    @Query("select b from Booking b where b.id = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") Long bookingId);

    @Query("""
        select count(b)
        from Booking b
        where b.user.id = :userId
          and b.status = :status
        """)
    long countByUserAndStatus(
        @Param("userId") Long userId,
        @Param("status") BookingStatus status);

    long countByStatus(BookingStatus status);

    @Query("""
        select count(distinct b.parkingSlot.id)
        from Booking b
        where b.status in :statuses
          and b.startTime <= :at
          and b.endTime > :at
        """)
    long countSlotsBookedAt(
        @Param("statuses") Collection<BookingStatus> statuses,
        @Param("at") LocalDateTime at);

    @Query("""
        select count(distinct b.parkingSlot.id)
        from Booking b
        where b.parkingSlot.status = com.smartpark.entity.ParkingSlotStatus.AVAILABLE
          and b.status in :statuses
          and b.startTime <= :at
          and b.endTime > :at
        """)
    long countAvailableSlotsBookedAt(
        @Param("statuses") Collection<BookingStatus> statuses,
        @Param("at") LocalDateTime at);

    @Query("""
        select count(distinct b.parkingSlot.id)
        from Booking b
        where b.status = :status
          and b.startTime > :at
        """)
    long countFutureReservedSlots(
        @Param("status") BookingStatus status,
        @Param("at") LocalDateTime at);

    @Query("""
        select coalesce(sum(b.amount), 0)
        from Booking b
        where b.status in :statuses
          and b.createdAt >= :from
          and b.createdAt < :to
        """)
    java.math.BigDecimal sumAmountByStatusAndCreatedAt(
        @Param("statuses") Collection<BookingStatus> statuses,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("""
        select coalesce(sum(b.amount), 0)
        from Booking b
        where b.status in :statuses
        """)
    java.math.BigDecimal sumAmountByStatuses(
        @Param("statuses") Collection<BookingStatus> statuses);

    @Query("""
        select b
        from Booking b
        where (:status is null or b.status = :status)
          and (:from is null or b.startTime >= :from)
          and (:to is null or b.startTime < :to)
        """)
    @EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot", "bookingGroup"})
    Page<Booking> searchForAdmin(
        @Param("status") BookingStatus status,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);

    @Query("""
        select b
        from Booking b
        where b.createdAt >= :from
          and b.createdAt < :to
          and b.status in :statuses
        order by b.createdAt
        """)
    List<Booking> findAnalyticsBookings(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        @Param("statuses") Collection<BookingStatus> statuses);

    @EntityGraph(attributePaths = {"user", "vehicle", "parkingSlot", "bookingGroup"})
    List<Booking> findAllByOrderByCreatedAtDesc();

    @Query("select s from ParkingSlot s where s.id = :slotId")
    Optional<ParkingSlot> findSlotById(@Param("slotId") Long slotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSlot s where s.id = :slotId")
    Optional<ParkingSlot> lockSlotById(@Param("slotId") Long slotId);
}
