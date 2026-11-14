package com.smartpark.controller;

import com.smartpark.entity.ParkingFacility;
import com.smartpark.security.CurrentUser;
import com.smartpark.service.OwnerVerificationService;
import com.smartpark.service.BookingService;
import com.smartpark.service.ParkingOwnerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/owner")
@PreAuthorize("hasRole('PARKING_OWNER')")
public class ParkingOwnerController {

    private final ParkingOwnerService ownerService;
    private final OwnerVerificationService verificationService;
    private final BookingService bookingService;

    public ParkingOwnerController(
            ParkingOwnerService ownerService,
            OwnerVerificationService verificationService,
            BookingService bookingService) {
        this.ownerService = ownerService;
        this.verificationService = verificationService;
        this.bookingService = bookingService;
    }

    @GetMapping("/verification")
    public OwnerVerificationService.VerificationView getVerification(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return verificationService.getOwnerVerification(currentUser.id());
    }

    @PostMapping("/verification/otp")
    public OwnerVerificationService.VerificationView sendVerificationOtp(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return verificationService.sendOtp(currentUser.id());
    }

    @PostMapping("/verification/otp/verify")
    public OwnerVerificationService.VerificationView verifyEmail(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody OtpRequest request) {
        return verificationService.verifyOtp(currentUser.id(), request.otp());
    }

    @PostMapping(
        path = "/verification/document",
        consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public OwnerVerificationService.VerificationView uploadGovernmentDocument(
            @AuthenticationPrincipal CurrentUser currentUser,
            @RequestPart("document") MultipartFile document) {
        return verificationService.uploadDocument(currentUser.id(), document);
    }

    @GetMapping("/facility")
    public FacilityResponse getFacility(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ownerService.getFacility(currentUser.id())
            .map(FacilityResponse::from)
            .orElse(null);
    }

    @PostMapping("/facility")
    @ResponseStatus(HttpStatus.CREATED)
    public FacilityResponse createFacility(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody FacilityRequest request) {
        return FacilityResponse.from(ownerService.createFacility(
            currentUser.id(), request.name(), request.address(),
            request.totalCapacity(), request.operatingStart(), request.operatingEnd(),
            request.latitude(), request.longitude()
        ));
    }

    @PutMapping("/facility")
    public FacilityResponse updateFacility(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody FacilityRequest request) {
        return FacilityResponse.from(ownerService.updateFacility(
            currentUser.id(), request.name(), request.address(),
            request.totalCapacity(), request.operatingStart(), request.operatingEnd()
        ));
    }

    @GetMapping("/facilities")
    public List<FacilityResponse> getFacilities(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ownerService.getFacilities(currentUser.id()).stream()
            .map(FacilityResponse::from)
            .toList();
    }

    @PostMapping("/facilities")
    @ResponseStatus(HttpStatus.CREATED)
    public FacilityResponse createAdditionalFacility(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody FacilityRequest request) {
        return FacilityResponse.from(ownerService.createFacility(
            currentUser.id(), request.name(), request.address(),
            request.totalCapacity(), request.operatingStart(), request.operatingEnd(),
            request.latitude(), request.longitude()
        ));
    }

    @PutMapping("/facilities/{facilityId}")
    public FacilityResponse updateFacility(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long facilityId,
            @Valid @RequestBody FacilityRequest request) {
        return FacilityResponse.from(ownerService.updateFacility(
            currentUser.id(), facilityId, request.name(), request.address(),
            request.totalCapacity(), request.operatingStart(), request.operatingEnd(),
            request.latitude(), request.longitude()
        ));
    }

    @PatchMapping("/facilities/{facilityId}/listing")
    public FacilityResponse updateFacilityListing(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long facilityId,
            @Valid @RequestBody FacilityListingRequest request) {
        return FacilityResponse.from(ownerService.setFacilityListing(
            currentUser.id(), facilityId, request.listed()
        ));
    }

    @GetMapping("/facilities/{facilityId}/overview")
    public OwnerOverviewResponse getFacilityOverview(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long facilityId) {
        return ownerOverview(ownerService.getOverview(currentUser.id(), facilityId));
    }

    @PostMapping("/facilities/{facilityId}/parking-slots")
    @ResponseStatus(HttpStatus.CREATED)
    public ParkingSlotController.ParkingSlotResponse createFacilitySlot(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long facilityId,
            @Valid @RequestBody ParkingSlotController.ParkingSlotRequest request) {
        return ParkingSlotController.ParkingSlotResponse.from(ownerService.createSlot(
            currentUser.id(), facilityId, request.slotNumber(), request.floorNumber(),
            request.location(), request.zone(), request.vehicleType(), request.hourlyPrice(),
            request.dailyPrice(), request.monthlyPrice(), request.status()
        ));
    }

    @PutMapping("/facilities/{facilityId}/parking-slots/{slotId}")
    public ParkingSlotController.ParkingSlotResponse updateFacilitySlot(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long facilityId,
            @PathVariable @Positive Long slotId,
            @Valid @RequestBody ParkingSlotController.ParkingSlotRequest request) {
        return ParkingSlotController.ParkingSlotResponse.from(ownerService.updateSlot(
            currentUser.id(), facilityId, slotId, request.slotNumber(), request.floorNumber(),
            request.location(), request.zone(), request.vehicleType(), request.hourlyPrice(),
            request.dailyPrice(), request.monthlyPrice(), request.status()
        ));
    }

    @DeleteMapping("/facilities/{facilityId}/parking-slots/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFacilitySlot(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long facilityId,
            @PathVariable @Positive Long slotId) {
        ownerService.deleteSlot(currentUser.id(), facilityId, slotId);
    }

    @PostMapping("/parking-slots")
    @ResponseStatus(HttpStatus.CREATED)
    public ParkingSlotController.ParkingSlotResponse createParkingSlot(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody ParkingSlotController.ParkingSlotRequest request) {
        return ParkingSlotController.ParkingSlotResponse.from(ownerService.createSlot(
            currentUser.id(), request.slotNumber(), request.floorNumber(),
            request.location(), request.zone(), request.vehicleType(),
            request.hourlyPrice(), request.dailyPrice(), request.monthlyPrice(),
            request.status()
        ));
    }

    @PutMapping("/parking-slots/{slotId}")
    public ParkingSlotController.ParkingSlotResponse updateParkingSlot(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long slotId,
            @Valid @RequestBody ParkingSlotController.ParkingSlotRequest request) {
        return ParkingSlotController.ParkingSlotResponse.from(ownerService.updateSlot(
            currentUser.id(), slotId, request.slotNumber(), request.floorNumber(),
            request.location(), request.zone(), request.vehicleType(),
            request.hourlyPrice(), request.dailyPrice(), request.monthlyPrice(),
            request.status()
        ));
    }

    @DeleteMapping("/parking-slots/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteParkingSlot(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long slotId) {
        ownerService.deleteSlot(currentUser.id(), slotId);
    }

    @PostMapping("/bookings/{bookingId}/start")
    public BookingController.BookingResponse startBookingSession(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long bookingId) {
        return BookingController.BookingResponse.from(
            bookingService.startSession(bookingId, currentUser.id())
        );
    }

    @PostMapping("/bookings/{bookingId}/complete")
    public BookingController.BookingResponse completeBookingSession(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long bookingId) {
        return BookingController.BookingResponse.from(
            bookingService.completeSession(bookingId, currentUser.id())
        );
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public BookingController.BookingResponse cancelBooking(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable @Positive Long bookingId) {
        return BookingController.BookingResponse.from(
            bookingService.cancelByOwner(bookingId, currentUser.id())
        );
    }

    @GetMapping("/overview")
    public OwnerOverviewResponse getOverview(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return ownerOverview(ownerService.getOverview(currentUser.id()));
    }

    private static OwnerOverviewResponse ownerOverview(ParkingOwnerService.OwnerOverview overview) {
        return new OwnerOverviewResponse(
            FacilityResponse.from(overview.facility()),
            overview.slots().stream()
                .map(ParkingSlotController.ParkingSlotResponse::from)
                .toList(),
            overview.bookings().stream()
                .map(BookingController.BookingResponse::from)
                .toList(),
            overview.settledEarnings()
        );
    }

    public record OtpRequest(
        @Pattern(regexp = "^\\d{6}$", message = "Enter the 6-digit email code")
        String otp
    ) {
    }

    public record FacilityRequest(
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 500) String address,
        @Positive int totalCapacity,
        @NotNull LocalTime operatingStart,
        @NotNull LocalTime operatingEnd,
        BigDecimal latitude,
        BigDecimal longitude
    ) {
    }

    public record FacilityListingRequest(@NotNull Boolean listed) {
    }

    public record FacilityResponse(
        Long id,
        String name,
        String address,
        int totalCapacity,
        LocalTime operatingStart,
        LocalTime operatingEnd,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean active
    ) {
        static FacilityResponse from(ParkingFacility facility) {
            return new FacilityResponse(
                facility.getId(), facility.getName(), facility.getAddress(),
                facility.getTotalCapacity(), facility.getOperatingStart(),
                facility.getOperatingEnd(), facility.getLatitude(), facility.getLongitude(),
                facility.isActive()
            );
        }
    }

    public record OwnerOverviewResponse(
        FacilityResponse facility,
        List<ParkingSlotController.ParkingSlotResponse> slots,
        List<BookingController.BookingResponse> bookings,
        java.math.BigDecimal settledEarnings
    ) {
    }
}
