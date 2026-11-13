
package com.smartpark.repository;

import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.BookingStatus;
import java.util.Collection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, Long> {

    boolean existsBySlotNumberIgnoreCase(String slotNumber);

    boolean existsBySlotNumberIgnoreCaseAndIdNot(String slotNumber, Long id);

    List<ParkingSlot> findAllByOrderByFloorNumberAscSlotNumberAsc();

    long countByStatus(com.smartpark.entity.ParkingSlotStatus status);
    long countByFacility_Id(Long facilityId);
    long countByFacility_IdAndStatus(Long facilityId, com.smartpark.entity.ParkingSlotStatus status);
    List<ParkingSlot> findAllByFacility_IdOrderByFloorNumberAscSlotNumberAsc(Long facilityId);

    @Query("""
        select s
        from ParkingSlot s
        where s.available = true
          and s.status = com.smartpark.entity.ParkingSlotStatus.AVAILABLE
          and s.facility.active = true
          and (:vehicleType is null or s.vehicleType is null
               or lower(s.vehicleType) = lower(:vehicleType))
          and not exists (
              select b.id
              from Booking b
              where b.parkingSlot = s
                and b.status in :statuses
                and (b.status <> com.smartpark.entity.BookingStatus.PENDING_PAYMENT
                     or b.holdExpiresAt > CURRENT_TIMESTAMP)
                and b.startTime < :endTime
                and b.endTime > :startTime
          )
        order by s.floorNumber, s.slotNumber
        """)
    @EntityGraph(attributePaths = "facility")
    List<ParkingSlot> findAvailableSlots(
        @Param("vehicleType") String vehicleType,
        @Param("statuses") Collection<BookingStatus> statuses,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);

    @Query("""
        select count(s)
        from ParkingSlot s
        where s.facility.id = :facilityId
          and s.status = com.smartpark.entity.ParkingSlotStatus.AVAILABLE
          and s.available = true
          and not exists (
              select b.id
              from Booking b
              where b.parkingSlot = s
                and (
                    b.status in (
                        com.smartpark.entity.BookingStatus.CONFIRMED,
                        com.smartpark.entity.BookingStatus.ACTIVE,
                        com.smartpark.entity.BookingStatus.OVERDUE
                    )
                    or (b.status = com.smartpark.entity.BookingStatus.PENDING_PAYMENT
                        and b.holdExpiresAt > :at)
                )
                and b.startTime <= :at
                and b.endTime > :at
          )
        """)
    long countAvailableByFacilityAt(
        @Param("facilityId") Long facilityId,
        @Param("at") LocalDateTime at);

    @Query("""
        select count(distinct b.parkingSlot.id)
        from Booking b
        where b.parkingSlot.facility.id = :facilityId
          and (
              b.status in (
                  com.smartpark.entity.BookingStatus.CONFIRMED,
                  com.smartpark.entity.BookingStatus.ACTIVE,
                  com.smartpark.entity.BookingStatus.OVERDUE
              )
              or (b.status = com.smartpark.entity.BookingStatus.PENDING_PAYMENT
                  and b.holdExpiresAt > :at)
          )
          and b.startTime <= :at
          and b.endTime > :at
        """)
    long countBookedByFacilityAt(
        @Param("facilityId") Long facilityId,
        @Param("at") LocalDateTime at);

}
