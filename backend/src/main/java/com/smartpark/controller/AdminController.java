package com.smartpark.controller;

import com.smartpark.entity.BookingStatus;
import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.User;
import com.smartpark.entity.Vehicle;
import com.smartpark.security.CurrentUser;
import com.smartpark.service.AdminService;
import com.smartpark.service.OwnerVerificationService;
import com.smartpark.service.ParkingSlotService;
import com.smartpark.service.SmartParkSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final ParkingSlotService slotService;
    private final SmartParkSettingsService settingsService;
    private final OwnerVerificationService ownerVerificationService;

    public AdminController(
            AdminService adminService,
            ParkingSlotService slotService,
            SmartParkSettingsService settingsService,
            OwnerVerificationService ownerVerificationService) {
        this.adminService = adminService;
        this.slotService = slotService;
        this.settingsService = settingsService;
        this.ownerVerificationService = ownerVerificationService;
    }

    @GetMapping("/settings")
    public SmartParkSettingsService.SettingsView getSettings() {
        return settingsService.getSettings();
    }

    @org.springframework.web.bind.annotation.PutMapping("/settings")
    public SmartParkSettingsService.SettingsView updateSettings(
            @Valid @RequestBody SmartParkSettingsService.SettingsUpdate update) {
        return settingsService.updateSettings(update);
    }

    @GetMapping("/owner-approvals")
    public List<OwnerVerificationService.ApprovalRequest> getOwnerApprovals() {
        return ownerVerificationService.getPendingApprovals();
    }

    @PostMapping("/owner-approvals/{ownerId}/approve")
    public OwnerVerificationService.ApprovalRequest approveOwner(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable @Positive Long ownerId) {
        return ownerVerificationService.approveManually(ownerId, admin.id());
    }

    @PostMapping("/owner-approvals/{ownerId}/reject")
    public OwnerVerificationService.ApprovalRequest rejectOwner(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable @Positive Long ownerId,
            @Valid @RequestBody RejectOwnerRequest request) {
        return ownerVerificationService.rejectManually(
            ownerId, admin.id(), request.reason()
        );
    }

    @GetMapping("/owner-approvals/{ownerId}/document")
    public ResponseEntity<byte[]> getOwnerDocument(
            @PathVariable @Positive Long ownerId) {
        var document = ownerVerificationService.getDocumentForAdmin(ownerId);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options", "nosniff")
            .contentType(MediaType.parseMediaType(document.contentType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                org.springframework.http.ContentDisposition.attachment()
                    .filename(document.fileName(), StandardCharsets.UTF_8)
                    .build()
                    .toString()
            )
            .body(document.content());
    }

    @GetMapping("/dashboard")
    public AdminService.DashboardMetrics getDashboard() {
        return adminService.getDashboard();
    }

    @GetMapping("/parking-slots")
    public List<ParkingSlotController.ParkingSlotResponse> getSlots() {
        return slotService.getAllSlots().stream()
            .map(ParkingSlotController.ParkingSlotResponse::from)
            .toList();
    }

    @PostMapping("/parking-slots")
    public ParkingSlotController.ParkingSlotResponse createSlot(
            @Valid @RequestBody ParkingSlotController.ParkingSlotRequest request) {
        ParkingSlot slot = slotService.createSlot(
            request.slotNumber(), request.floorNumber(), request.location(),
            request.zone(), request.vehicleType(), request.hourlyPrice(),
            request.dailyPrice(), request.monthlyPrice(), request.status()
        );
        return ParkingSlotController.ParkingSlotResponse.from(slot);
    }

    @PatchMapping("/parking-slots/{slotId}")
    public ParkingSlotController.ParkingSlotResponse updateSlot(
            @PathVariable @Positive Long slotId,
            @Valid @RequestBody ParkingSlotController.ParkingSlotRequest request) {
        ParkingSlot slot = slotService.updateSlot(
            slotId, request.slotNumber(), request.floorNumber(),
            request.location(), request.zone(), request.vehicleType(),
            request.hourlyPrice(), request.dailyPrice(), request.monthlyPrice(),
            request.status()
        );
        return ParkingSlotController.ParkingSlotResponse.from(slot);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/parking-slots/{slotId}")
    @org.springframework.web.bind.annotation.ResponseStatus(
        org.springframework.http.HttpStatus.NO_CONTENT
    )
    public void deleteSlot(@PathVariable @Positive Long slotId) {
        slotService.deleteSlot(slotId);
    }

    @GetMapping("/bookings")
    public PageResponse<BookingController.BookingResponse> getBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive int size) {
        Pageable pageable = PageRequest.of(
            page, Math.min(size, 100),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return PageResponse.from(adminService.searchBookings(
            status, from, to, pageable
        ).map(BookingController.BookingResponse::from));
    }

    @GetMapping("/bookings/{bookingId}")
    public BookingController.BookingResponse getBooking(
            @PathVariable @Positive Long bookingId) {
        return BookingController.BookingResponse.from(
            adminService.getBooking(bookingId)
        );
    }

    @PostMapping("/bookings/{bookingId}/start")
    public BookingController.BookingResponse startBooking(
            @PathVariable @Positive Long bookingId) {
        return BookingController.BookingResponse.from(
            adminService.startBooking(bookingId)
        );
    }

    @PostMapping("/bookings/{bookingId}/complete")
    public BookingController.BookingResponse completeBooking(
            @PathVariable @Positive Long bookingId) {
        return BookingController.BookingResponse.from(
            adminService.completeBooking(bookingId)
        );
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    public BookingController.BookingResponse cancelBooking(
            @PathVariable @Positive Long bookingId) {
        return BookingController.BookingResponse.from(
            adminService.cancelBooking(bookingId)
        );
    }

    @GetMapping("/users")
    public PageResponse<AdminUserResponse> getUsers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Positive int size) {
        Pageable pageable = PageRequest.of(
            page, Math.min(size, 100),
            Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return PageResponse.from(adminService.searchUsers(search, pageable)
            .map(AdminUserResponse::from));
    }

    @GetMapping("/users/unverified-owners")
    public List<AdminUserResponse> getUnverifiedOwnerAccounts() {
        return adminService.getUnverifiedOwnerAccounts().stream()
            .map(AdminUserResponse::from)
            .toList();
    }

    @GetMapping("/users/{userId}")
    public AdminUserResponse getUser(@PathVariable @Positive Long userId) {
        return AdminUserResponse.from(adminService.getUser(userId));
    }

    @GetMapping("/users/{userId}/bookings")
    public List<BookingController.BookingResponse> getUserBookings(
            @PathVariable @Positive Long userId) {
        return adminService.getUserBookings(userId).stream()
            .map(BookingController.BookingResponse::from)
            .toList();
    }

    @PatchMapping("/users/{userId}/status")
    public AdminUserResponse updateUserStatus(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable @Positive Long userId,
            @Valid @RequestBody UserStatusRequest request) {
        return AdminUserResponse.from(adminService.updateUserStatus(
            userId, admin.id(), request.enabled()
        ));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/users/{userId}")
    @org.springframework.web.bind.annotation.ResponseStatus(
        org.springframework.http.HttpStatus.NO_CONTENT
    )
    public void deleteUser(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable @Positive Long userId) {
        adminService.deleteUser(userId, admin.id());
    }

    @GetMapping("/vehicles/unassigned")
    public List<UnassignedVehicleResponse> getUnassignedVehicles() {
        return adminService.getUnassignedVehicles().stream()
            .map(UnassignedVehicleResponse::from)
            .toList();
    }

    @PatchMapping("/vehicles/{vehicleId}/owner")
    public UnassignedVehicleResponse assignVehicle(
            @PathVariable @Positive Long vehicleId,
            @Valid @RequestBody AssignVehicleRequest request) {
        return UnassignedVehicleResponse.from(
            adminService.assignVehicleToUser(vehicleId, request.userId())
        );
    }

    @GetMapping("/analytics")
    public AdminService.AnalyticsResponse getAnalytics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to,
            @RequestParam(defaultValue = "DAY") AdminService.GroupBy groupBy) {
        return adminService.getAnalytics(from, to, groupBy);
    }

    public record UserStatusRequest(@NotNull Boolean enabled) {
    }

    public record RejectOwnerRequest(
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record AssignVehicleRequest(@NotNull @Positive Long userId) {
    }

    public record AdminUserResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        boolean enabled,
        LocalDateTime createdAt
    ) {
        static AdminUserResponse from(User user) {
            return new AdminUserResponse(
                user.getId(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getRole(), user.isEnabled(),
                user.getCreatedAt()
            );
        }
    }

    public record UnassignedVehicleResponse(
        Long id,
        String legacyOwnerName,
        String vehicleNumber,
        String vehicleType,
        String brand,
        String model,
        String color
    ) {
        static UnassignedVehicleResponse from(Vehicle vehicle) {
            return new UnassignedVehicleResponse(
                vehicle.getId(), vehicle.getOwnerName(),
                vehicle.getVehicleNumber(), vehicle.getVehicleType(),
                vehicle.getBrand(), vehicle.getModel(), vehicle.getColor()
            );
        }
    }
}
