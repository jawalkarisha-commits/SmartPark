package com.smartpark.service;

import com.smartpark.entity.Booking;
import com.smartpark.entity.OwnerVerification.ApprovalStatus;
import com.smartpark.entity.ParkingFacility;
import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.ParkingSlotStatus;
import com.smartpark.repository.BookingRepository;
import com.smartpark.repository.OwnerVerificationRepository;
import com.smartpark.repository.ParkingFacilityRepository;
import com.smartpark.repository.ParkingSlotRepository;
import com.smartpark.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParkingOwnerService {

    private final ParkingFacilityRepository facilityRepository;
    private final ParkingSlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final OwnerVerificationRepository verificationRepository;
    private final UserRepository userRepository;
    private final ParkingSlotService slotService;

    public ParkingOwnerService(
            ParkingFacilityRepository facilityRepository,
            ParkingSlotRepository slotRepository,
            BookingRepository bookingRepository,
            OwnerVerificationRepository verificationRepository,
            UserRepository userRepository,
            ParkingSlotService slotService) {
        this.facilityRepository = facilityRepository;
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.verificationRepository = verificationRepository;
        this.userRepository = userRepository;
        this.slotService = slotService;
    }

    @Transactional(readOnly = true)
    public Optional<ParkingFacility> getFacility(Long ownerId) {
        requireApproved(ownerId);
        return facilityRepository.findFirstByOwner_IdOrderByIdAsc(ownerId);
    }

    @Transactional
    public ParkingFacility createFacility(
            Long ownerId, String name, String address, int totalCapacity,
            LocalTime operatingStart, LocalTime operatingEnd) {
        return createFacility(ownerId, name, address, totalCapacity,
            operatingStart, operatingEnd, null, null);
    }

    @Transactional
    public ParkingFacility updateFacility(
            Long ownerId, String name, String address, int totalCapacity,
            LocalTime operatingStart, LocalTime operatingEnd) {
        ParkingFacility facility = getOwnedFacility(ownerId);
        return updateFacility(ownerId, facility.getId(), name, address,
            totalCapacity, operatingStart, operatingEnd,
            facility.getLatitude(), facility.getLongitude());
    }

    @Transactional(readOnly = true)
    public List<ParkingFacility> getFacilities(Long ownerId) {
        requireApproved(ownerId);
        return facilityRepository.findAllByOwner_IdOrderByIdAsc(ownerId);
    }

    @Transactional
    public ParkingFacility createFacility(
            Long ownerId, String name, String address, int totalCapacity,
            LocalTime operatingStart, LocalTime operatingEnd,
            BigDecimal latitude, BigDecimal longitude) {
        requireApproved(ownerId);
        validateFacility(name, address, totalCapacity, operatingStart, operatingEnd);
        validateCoordinates(latitude, longitude);
        if (facilityRepository.findAllByOwner_IdOrderByIdAsc(ownerId).size() >= 50) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "An owner may manage up to 50 facilities"
            );
        }
        ParkingFacility facility = new ParkingFacility();
        facility.setName(name.trim());
        facility.setAddress(address.trim());
        facility.setTotalCapacity(totalCapacity);
        facility.setOperatingStart(operatingStart);
        facility.setOperatingEnd(operatingEnd);
        facility.setLatitude(latitude);
        facility.setLongitude(longitude);
        facility.setOwner(userRepository.findById(ownerId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Parking owner account not found"
            )));
        return facilityRepository.save(facility);
    }

    @Transactional
    public ParkingFacility updateFacility(
            Long ownerId, Long facilityId, String name, String address, int totalCapacity,
            LocalTime operatingStart, LocalTime operatingEnd,
            BigDecimal latitude, BigDecimal longitude) {
        validateFacility(name, address, totalCapacity, operatingStart, operatingEnd);
        validateCoordinates(latitude, longitude);
        ParkingFacility facility = getOwnedFacility(ownerId, facilityId);
        if (totalCapacity < slotRepository.countByFacility_Id(facility.getId())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Facility capacity cannot be less than the number of listed spaces"
            );
        }
        facility.setName(name.trim());
        facility.setAddress(address.trim());
        facility.setTotalCapacity(totalCapacity);
        facility.setOperatingStart(operatingStart);
        facility.setOperatingEnd(operatingEnd);
        facility.setLatitude(latitude);
        facility.setLongitude(longitude);
        return facility;
    }

    @Transactional(readOnly = true)
    public OwnerOverview getOverview(Long ownerId, Long facilityId) {
        ParkingFacility facility = getOwnedFacility(ownerId, facilityId);
        return new OwnerOverview(
            facility,
            slotRepository.findAllByFacility_IdOrderByFloorNumberAscSlotNumberAsc(facility.getId()),
            bookingRepository.findTop100ByParkingSlot_Facility_IdOrderByCreatedAtDesc(facility.getId()),
            bookingRepository.sumSettledOwnerEarnings(ownerId)
        );
    }

    @Transactional
    public ParkingSlot createSlot(
            Long ownerId, Long facilityId, String slotNumber, Integer floorNumber, String location,
            String zone, String vehicleType, BigDecimal hourlyPrice,
            BigDecimal dailyPrice, BigDecimal monthlyPrice, ParkingSlotStatus status) {
        getOwnedFacility(ownerId, facilityId);
        return slotService.createSlotInFacility(
            facilityId, slotNumber, floorNumber, location, zone,
            vehicleType, hourlyPrice, dailyPrice, monthlyPrice, status
        );
    }

    @Transactional
    public ParkingSlot updateSlot(
            Long ownerId, Long facilityId, Long slotId, String slotNumber,
            Integer floorNumber, String location, String zone, String vehicleType,
            BigDecimal hourlyPrice, BigDecimal dailyPrice, BigDecimal monthlyPrice,
            ParkingSlotStatus status) {
        getOwnedFacility(ownerId, facilityId);
        return slotService.updateSlotForFacility(
            facilityId, slotId, slotNumber, floorNumber, location, zone,
            vehicleType, hourlyPrice, dailyPrice, monthlyPrice, status
        );
    }

    @Transactional
    public void deleteSlot(Long ownerId, Long facilityId, Long slotId) {
        getOwnedFacility(ownerId, facilityId);
        slotService.deleteSlotForFacility(facilityId, slotId);
    }

    @Transactional
    public ParkingSlot createSlot(
            Long ownerId, String slotNumber, Integer floorNumber, String location,
            String zone, String vehicleType, BigDecimal hourlyPrice,
            BigDecimal dailyPrice, BigDecimal monthlyPrice, ParkingSlotStatus status) {
        ParkingFacility facility = getOwnedFacility(ownerId);
        return slotService.createSlotInFacility(
            facility.getId(), slotNumber, floorNumber, location, zone,
            vehicleType, hourlyPrice, dailyPrice, monthlyPrice, status
        );
    }

    @Transactional
    public ParkingSlot updateSlot(
            Long ownerId, Long slotId, String slotNumber, Integer floorNumber,
            String location, String zone, String vehicleType, BigDecimal hourlyPrice,
            BigDecimal dailyPrice, BigDecimal monthlyPrice, ParkingSlotStatus status) {
        ParkingFacility facility = getOwnedFacility(ownerId);
        return slotService.updateSlotForFacility(
            facility.getId(), slotId, slotNumber, floorNumber, location, zone,
            vehicleType, hourlyPrice, dailyPrice, monthlyPrice, status
        );
    }

    @Transactional
    public void deleteSlot(Long ownerId, Long slotId) {
        ParkingFacility facility = getOwnedFacility(ownerId);
        slotService.deleteSlotForFacility(facility.getId(), slotId);
    }

    @Transactional(readOnly = true)
    public OwnerOverview getOverview(Long ownerId) {
        ParkingFacility facility = getOwnedFacility(ownerId);
        return new OwnerOverview(
            facility,
            slotRepository.findAllByFacility_IdOrderByFloorNumberAscSlotNumberAsc(
                facility.getId()
            ),
            bookingRepository.findTop100ByParkingSlot_Facility_IdOrderByCreatedAtDesc(
                facility.getId()
            ),
            bookingRepository.sumSettledOwnerEarnings(ownerId)
        );
    }

    private ParkingFacility getOwnedFacility(Long ownerId) {
        requireApproved(ownerId);
        return facilityRepository.findFirstByOwner_IdOrderByIdAsc(ownerId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.CONFLICT, "Create or request assignment of your parking facility first"
            ));
    }

    private ParkingFacility getOwnedFacility(Long ownerId, Long facilityId) {
        requireApproved(ownerId);
        return facilityRepository.findById(facilityId)
            .filter(facility -> facility.getOwner() != null
                && facility.getOwner().getId().equals(ownerId))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Parking facility not found"
            ));
    }

    private void requireApproved(Long ownerId) {
        boolean approved = verificationRepository.findByOwner_Id(ownerId)
            .map(verification -> verification.getApprovalStatus() == ApprovalStatus.APPROVED)
            .orElse(false);
        if (!approved) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Complete owner verification or wait for administrator approval before managing parking listings"
            );
        }
    }

    private static void validateFacility(
            String name, String address, int totalCapacity,
            LocalTime operatingStart, LocalTime operatingEnd) {
        if (name == null || name.isBlank() || name.length() > 160
                || address == null || address.isBlank() || address.length() > 500
                || totalCapacity <= 0 || operatingStart == null || operatingEnd == null
                || operatingStart.equals(operatingEnd)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Provide valid facility details and operating hours"
            );
        }
    }

    private static void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)
                || latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                    || latitude.compareTo(BigDecimal.valueOf(90)) > 0)
                || longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                    || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Provide both valid latitude and longitude coordinates"
            );
        }
    }

    public record OwnerOverview(
        ParkingFacility facility,
        List<ParkingSlot> slots,
        List<Booking> bookings,
        BigDecimal settledEarnings
    ) {
    }
}
