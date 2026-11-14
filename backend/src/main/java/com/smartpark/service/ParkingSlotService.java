package com.smartpark.service;

import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.ParkingSlotStatus;
import com.smartpark.repository.BookingRepository;
import com.smartpark.repository.ParkingFacilityRepository;
import com.smartpark.repository.ParkingSlotRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParkingSlotService {

    private final ParkingSlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final ParkingFacilityRepository facilityRepository;

    public ParkingSlotService(
            ParkingSlotRepository slotRepository,
            BookingRepository bookingRepository,
            ParkingFacilityRepository facilityRepository) {
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.facilityRepository = facilityRepository;
    }

    @Transactional(readOnly = true)
    public List<ParkingSlot> getAllSlots() {
        return slotRepository.findAllByOrderByFloorNumberAscSlotNumberAsc();
    }

    @Transactional(readOnly = true)
    public ParkingSlot getSlot(Long id) {
        return slotRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Parking slot not found"
            ));
    }

    @Transactional
    public ParkingSlot createSlot(
            String slotNumber,
            Integer floorNumber,
            String location,
            String zone,
            String vehicleType,
            BigDecimal hourlyPrice,
            ParkingSlotStatus status) {
        return createSlot(slotNumber, floorNumber, location, zone, vehicleType,
            hourlyPrice, derivedRate(hourlyPrice, 24),
            derivedRate(hourlyPrice, 720), status);
    }

    @Transactional
    public ParkingSlot createSlot(
            String slotNumber, Integer floorNumber, String location, String zone,
            String vehicleType, BigDecimal hourlyPrice, BigDecimal dailyPrice,
            BigDecimal monthlyPrice, ParkingSlotStatus status) {
        var facility = facilityRepository.findFirstByOrderByIdAsc()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "No parking facility is configured"
            ));
        return createSlotInFacility(
            facility.getId(), slotNumber, floorNumber, location, zone,
            vehicleType, hourlyPrice, dailyPrice, monthlyPrice, status
        );
    }

    @Transactional
    public ParkingSlot createSlotInFacility(
            Long facilityId, String slotNumber, Integer floorNumber, String location,
            String zone, String vehicleType, BigDecimal hourlyPrice,
            BigDecimal dailyPrice, BigDecimal monthlyPrice,
            ParkingSlotStatus status) {
        validateSlot(slotNumber, hourlyPrice, dailyPrice, monthlyPrice);
        if (slotRepository.existsBySlotNumberIgnoreCase(slotNumber)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Parking slot number already exists"
            );
        }
        ParkingSlot slot = new ParkingSlot();
        apply(slot, slotNumber, floorNumber, location, zone,
            vehicleType, hourlyPrice, dailyPrice, monthlyPrice, status);
        var facility = facilityRepository.findById(facilityId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Parking facility not found"
            ));
        if (slotRepository.countByFacility_Id(facility.getId())
                >= facility.getTotalCapacity()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Facility capacity has been reached"
            );
        }
        slot.setFacility(facility);
        return slotRepository.save(slot);
    }

    @Transactional
    public ParkingSlot updateSlot(
            Long id,
            String slotNumber,
            Integer floorNumber,
            String location,
            String zone,
            String vehicleType,
            BigDecimal hourlyPrice,
            ParkingSlotStatus status) {
        return updateSlot(id, slotNumber, floorNumber, location, zone, vehicleType,
            hourlyPrice, derivedRate(hourlyPrice, 24),
            derivedRate(hourlyPrice, 720), status);
    }

    @Transactional
    public ParkingSlot updateSlot(
            Long id, String slotNumber, Integer floorNumber, String location,
            String zone, String vehicleType, BigDecimal hourlyPrice,
            BigDecimal dailyPrice, BigDecimal monthlyPrice, ParkingSlotStatus status) {
        validateSlot(slotNumber, hourlyPrice, dailyPrice, monthlyPrice);
        ParkingSlot slot = getSlot(id);
        if (slotRepository.existsBySlotNumberIgnoreCaseAndIdNot(slotNumber, id)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Parking slot number already exists"
            );
        }
        apply(slot, slotNumber, floorNumber, location, zone,
            vehicleType, hourlyPrice, dailyPrice, monthlyPrice, status);
        return slot;
    }

    @Transactional
    public ParkingSlot updateSlotForFacility(
            Long facilityId, Long slotId, String slotNumber, Integer floorNumber,
            String location, String zone, String vehicleType, BigDecimal hourlyPrice,
            BigDecimal dailyPrice, BigDecimal monthlyPrice, ParkingSlotStatus status) {
        ParkingSlot slot = getSlot(slotId);
        if (!slot.getFacility().getId().equals(facilityId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parking slot not found");
        }
        return updateSlot(
            slotId, slotNumber, floorNumber, location, zone, vehicleType,
            hourlyPrice, dailyPrice, monthlyPrice, status
        );
    }

    @Transactional
    public void deleteSlot(Long id) {
        ParkingSlot slot = getSlot(id);
        if (bookingRepository.existsByParkingSlot_Id(id)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Parking slots with booking history cannot be deleted"
            );
        }
        slotRepository.delete(slot);
    }

    @Transactional
    public void deleteSlotForFacility(Long facilityId, Long slotId) {
        ParkingSlot slot = getSlot(slotId);
        if (!slot.getFacility().getId().equals(facilityId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Parking slot not found");
        }
        deleteSlot(slotId);
    }

    private static void validateSlot(
            String slotNumber,
            BigDecimal hourlyPrice,
            BigDecimal dailyPrice,
            BigDecimal monthlyPrice) {
        if (slotNumber == null || slotNumber.isBlank()
                || slotNumber.length() > 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Slot number is required and must be at most 100 characters"
            );
        }
        if (hourlyPrice == null || hourlyPrice.signum() <= 0
                || hourlyPrice.scale() > 2
                || hourlyPrice.precision() > 10) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Hourly price must be positive with at most two decimal places"
            );
        }
        validateRate("Daily price", dailyPrice);
        validateRate("Monthly price", monthlyPrice);
    }

    private static void validateRate(String label, BigDecimal value) {
        if (value == null || value.signum() <= 0
                || value.scale() > 2 || value.precision() > 10) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                label + " must be positive with at most two decimal places"
            );
        }
    }

    private static BigDecimal derivedRate(BigDecimal hourlyPrice, long hours) {
        return hourlyPrice == null
            ? BigDecimal.ZERO : hourlyPrice.multiply(BigDecimal.valueOf(hours));
    }

    private static void apply(
            ParkingSlot slot,
            String slotNumber,
            Integer floorNumber,
            String location,
            String zone,
            String vehicleType,
            BigDecimal hourlyPrice,
            BigDecimal dailyPrice,
            BigDecimal monthlyPrice,
            ParkingSlotStatus status) {
        slot.setSlotNumber(slotNumber.trim().toUpperCase());
        slot.setFloorNumber(floorNumber);
        slot.setLocation(trimToNull(location));
        slot.setZone(trimToNull(zone));
        slot.setVehicleType(trimToNull(vehicleType));
        slot.setHourlyPrice(hourlyPrice.setScale(2));
        slot.setDailyPrice(dailyPrice.setScale(2));
        slot.setMonthlyPrice(monthlyPrice.setScale(2));
        slot.setStatus(status);
        slot.setAvailable(status == ParkingSlotStatus.AVAILABLE);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
