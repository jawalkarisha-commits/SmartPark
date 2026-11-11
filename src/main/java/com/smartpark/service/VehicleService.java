package com.smartpark.service;

import com.smartpark.entity.User;
import com.smartpark.entity.Vehicle;
import com.smartpark.repository.BookingRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.repository.VehicleRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public VehicleService(
            VehicleRepository vehicleRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository) {
        this.vehicleRepository = vehicleRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public List<Vehicle> getVehiclesForUser(Long userId) {
        return vehicleRepository.findByOwner_IdOrderByVehicleNumber(userId);
    }

    @Transactional
    public Vehicle createVehicle(
            Long userId,
            String vehicleNumber,
            String vehicleType,
            String brand,
            String model,
            String color) {
        if (vehicleRepository.existsByVehicleNumberIgnoreCase(vehicleNumber)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Vehicle registration is already in use"
            );
        }
        User owner = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "User not found"
            ));

        Vehicle vehicle = new Vehicle();
        vehicle.setOwner(owner);
        vehicle.setVehicleNumber(vehicleNumber.trim().toUpperCase());
        vehicle.setVehicleType(vehicleType.trim());
        vehicle.setBrand(trimToNull(brand));
        vehicle.setModel(trimToNull(model));
        vehicle.setColor(trimToNull(color));
        return vehicleRepository.save(vehicle);
    }

    @Transactional(readOnly = true)
    public Vehicle getVehicle(Long vehicleId, Long userId) {
        return vehicleRepository.findByIdAndOwner_Id(vehicleId, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Vehicle not found"
            ));
    }

    @Transactional
    public Vehicle updateVehicle(
            Long vehicleId,
            Long userId,
            String vehicleNumber,
            String vehicleType,
            String brand,
            String model,
            String color) {
        Vehicle vehicle = getVehicle(vehicleId, userId);
        if (vehicleRepository.existsByVehicleNumberIgnoreCaseAndIdNot(
                vehicleNumber, vehicleId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Vehicle registration is already in use"
            );
        }
        vehicle.setVehicleNumber(vehicleNumber.trim().toUpperCase());
        vehicle.setVehicleType(vehicleType.trim());
        vehicle.setBrand(trimToNull(brand));
        vehicle.setModel(trimToNull(model));
        vehicle.setColor(trimToNull(color));
        return vehicle;
    }

    @Transactional
    public void deleteVehicle(Long vehicleId, Long userId) {
        Vehicle vehicle = getVehicle(vehicleId, userId);
        if (bookingRepository.existsByVehicle_Id(vehicleId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Vehicles with booking history cannot be deleted"
            );
        }
        vehicleRepository.delete(vehicle);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
