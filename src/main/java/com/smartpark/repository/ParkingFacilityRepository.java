package com.smartpark.repository;

import com.smartpark.entity.ParkingFacility;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingFacilityRepository extends JpaRepository<ParkingFacility, Long> {
    Optional<ParkingFacility> findFirstByOrderByIdAsc();
    Optional<ParkingFacility> findByOwner_Id(Long ownerId);
    Optional<ParkingFacility> findFirstByOwner_IdOrderByIdAsc(Long ownerId);
    List<ParkingFacility> findAllByActiveTrueAndLatitudeIsNotNullAndLongitudeIsNotNull();
    List<ParkingFacility> findAllByOwner_IdOrderByIdAsc(Long ownerId);
}
