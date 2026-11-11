package com.smartpark.service;

import com.smartpark.entity.ParkingFacility;
import com.smartpark.entity.SmartParkSettings;
import com.smartpark.entity.User;
import com.smartpark.repository.ParkingFacilityRepository;
import com.smartpark.repository.ParkingSlotRepository;
import com.smartpark.repository.SmartParkSettingsRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.repository.OwnerVerificationRepository;
import com.smartpark.entity.OwnerVerification.ApprovalStatus;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SmartParkSettingsService {

    private final ParkingFacilityRepository facilityRepository;
    private final ParkingSlotRepository slotRepository;
    private final SmartParkSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final OwnerVerificationRepository verificationRepository;

    public SmartParkSettingsService(
            ParkingFacilityRepository facilityRepository,
            ParkingSlotRepository slotRepository,
            SmartParkSettingsRepository settingsRepository,
            UserRepository userRepository,
            OwnerVerificationRepository verificationRepository) {
        this.facilityRepository = facilityRepository;
        this.slotRepository = slotRepository;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.verificationRepository = verificationRepository;
    }

    @Transactional(readOnly = true)
    public SettingsView getSettings() {
        ParkingFacility facility = getFacility();
        SmartParkSettings settings = getPolicy();
        long available = slotRepository.countAvailableByFacilityAt(
            facility.getId(), LocalDateTime.now()
        );
        return view(facility, settings, available);
    }

    @Transactional
    public SettingsView updateSettings(SettingsUpdate update) {
        validate(update);
        ParkingFacility facility = getFacility();
        long configuredSlots = slotRepository.countByFacility_Id(facility.getId());
        if (update.totalCapacity() < configuredSlots) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Total capacity cannot be less than the number of configured slots"
            );
        }

        facility.setName(update.facilityName().trim());
        facility.setAddress(update.address().trim());
        facility.setTotalCapacity(update.totalCapacity());
        facility.setOperatingStart(update.operatingStart());
        facility.setOperatingEnd(update.operatingEnd());
        if (update.ownerUserId() == null) {
            facility.setOwner(null);
        } else {
            User owner = userRepository.findById(update.ownerUserId())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Parking owner account not found"
                ));
            if (!"PARKING_OWNER".equals(owner.getRole()) || !owner.isEnabled()) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Assign an enabled parking-owner account"
                );
            }
            boolean approved = verificationRepository.findByOwner_Id(owner.getId())
                .map(verification ->
                    verification.getApprovalStatus() == ApprovalStatus.APPROVED)
                .orElse(false);
            if (!approved) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an approved parking owner can be assigned to a facility"
                );
            }
            facility.setOwner(owner);
        }

        SmartParkSettings settings = getPolicy();
        settings.setCurrencyCode("INR");
        settings.setOwnerRegistrationEnabled(update.ownerRegistrationEnabled());
        settings.setCancellationEnabled(update.cancellationEnabled());
        settings.setCancellationCutoffMinutes(update.cancellationCutoffMinutes());
        settings.setRefundPercentage(update.refundPercentage().setScale(2));
        settings.setVehicleTypes(String.join(",", update.vehicleTypes()));
        return view(facility, settings, slotRepository.countAvailableByFacilityAt(
            facility.getId(), LocalDateTime.now()
        ));
    }

    @Transactional(readOnly = true)
    public List<String> getVehicleTypes() {
        return Arrays.stream(getPolicy().getVehicleTypes().split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    private ParkingFacility getFacility() {
        return facilityRepository.findFirstByOrderByIdAsc()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "No parking facility is configured"
            ));
    }

    private SmartParkSettings getPolicy() {
        return settingsRepository.findById(1)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "SmartPark settings are not initialized"
            ));
    }

    private SettingsView view(
            ParkingFacility facility,
            SmartParkSettings settings,
            long availableSlots) {
        return new SettingsView(
            facility.getId(), facility.getName(), facility.getAddress(),
            facility.getTotalCapacity(), availableSlots,
            facility.getOperatingStart(), facility.getOperatingEnd(),
            facility.getOwner() == null ? null : facility.getOwner().getId(),
            settings.getCurrencyCode(), settings.isOwnerRegistrationEnabled(),
            settings.isCancellationEnabled(), settings.getCancellationCutoffMinutes(),
            settings.getRefundPercentage(), getVehicleTypes()
        );
    }

    private static void validate(SettingsUpdate update) {
        if (update == null || update.facilityName() == null
                || update.facilityName().isBlank() || update.facilityName().length() > 160
                || update.address() == null || update.address().isBlank()
                || update.address().length() > 500 || update.totalCapacity() < 0
                || update.operatingStart() == null || update.operatingEnd() == null
                || update.operatingStart().equals(update.operatingEnd())
                || update.cancellationCutoffMinutes() < 0
                || update.refundPercentage() == null
                || update.refundPercentage().compareTo(BigDecimal.ZERO) < 0
                || update.refundPercentage().compareTo(BigDecimal.valueOf(100)) > 0
                || update.vehicleTypes() == null || update.vehicleTypes().isEmpty()
                || update.vehicleTypes().size() > 20
                || update.vehicleTypes().stream().anyMatch(type ->
                    type == null || type.isBlank() || type.length() > 40)
                || String.join(",", update.vehicleTypes()).length() > 500) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Invalid facility or SmartPark policy settings"
            );
        }
        if (update.refundPercentage().scale() > 2) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Refund percentage supports at most two decimals"
            );
        }
    }

    public record SettingsUpdate(
        String facilityName,
        String address,
        int totalCapacity,
        LocalTime operatingStart,
        LocalTime operatingEnd,
        Long ownerUserId,
        boolean ownerRegistrationEnabled,
        boolean cancellationEnabled,
        int cancellationCutoffMinutes,
        BigDecimal refundPercentage,
        List<String> vehicleTypes
    ) {
    }

    public record SettingsView(
        Long facilityId,
        String facilityName,
        String address,
        int totalCapacity,
        long availableSlots,
        LocalTime operatingStart,
        LocalTime operatingEnd,
        Long ownerUserId,
        String currencyCode,
        boolean ownerRegistrationEnabled,
        boolean cancellationEnabled,
        int cancellationCutoffMinutes,
        BigDecimal refundPercentage,
        List<String> vehicleTypes
    ) {
    }
}
