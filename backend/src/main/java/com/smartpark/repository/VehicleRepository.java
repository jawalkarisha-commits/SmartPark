package com.smartpark.repository;

import com.smartpark.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByOwner_IdOrderByVehicleNumber(Long userId);

    Optional<Vehicle> findByIdAndOwner_Id(Long id, Long userId);

    boolean existsByVehicleNumberIgnoreCase(String vehicleNumber);

    boolean existsByVehicleNumberIgnoreCaseAndIdNot(
        String vehicleNumber,
        Long id);

    List<Vehicle> findAllByOwnerIsNullOrderByIdAsc();
}
