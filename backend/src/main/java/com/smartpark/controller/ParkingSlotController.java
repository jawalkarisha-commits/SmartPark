package com.smartpark.controller;

import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.ParkingSlotStatus;
import com.smartpark.service.ParkingSlotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parking-slots")
public class ParkingSlotController {

    private final ParkingSlotService slotService;

    public ParkingSlotController(ParkingSlotService slotService) {
        this.slotService = slotService;
    }

    @GetMapping
    public List<ParkingSlotResponse> getAllSlots() {
        return slotService.getAllSlots().stream()
            .map(ParkingSlotResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public ParkingSlotResponse getSlot(@PathVariable Long id) {
        return ParkingSlotResponse.from(slotService.getSlot(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ParkingSlotResponse addSlot(
            @Valid @RequestBody ParkingSlotRequest request) {
        return ParkingSlotResponse.from(slotService.createSlot(
            request.slotNumber(), request.floorNumber(), request.location(),
            request.zone(), request.vehicleType(), request.hourlyPrice(),
            request.dailyPrice(), request.monthlyPrice(), request.status()
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ParkingSlotResponse updateSlot(
            @PathVariable Long id,
            @Valid @RequestBody ParkingSlotRequest request) {
        return ParkingSlotResponse.from(slotService.updateSlot(
            id, request.slotNumber(), request.floorNumber(), request.location(),
            request.zone(), request.vehicleType(), request.hourlyPrice(),
            request.dailyPrice(), request.monthlyPrice(), request.status()
        ));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteSlot(@PathVariable Long id) {
        slotService.deleteSlot(id);
    }

    public record ParkingSlotRequest(
        @NotBlank @Size(max = 100) String slotNumber,
        @PositiveOrZero Integer floorNumber,
        @Size(max = 255) String location,
        @Size(max = 100) String zone,
        @Size(max = 40) String vehicleType,
        @NotNull @DecimalMin(value = "0.01") BigDecimal hourlyPrice,
        @NotNull @DecimalMin(value = "0.01") BigDecimal dailyPrice,
        @NotNull @DecimalMin(value = "0.01") BigDecimal monthlyPrice,
        @NotNull ParkingSlotStatus status
    ) {
    }

    public record ParkingSlotResponse(
        Long id,
        String slotNumber,
        Integer floorNumber,
        String location,
        String zone,
        String vehicleType,
        BigDecimal hourlyPrice,
        BigDecimal dailyPrice,
        BigDecimal monthlyPrice,
        ParkingSlotStatus status,
        boolean available
    ) {
        static ParkingSlotResponse from(ParkingSlot slot) {
            return new ParkingSlotResponse(
                slot.getId(), slot.getSlotNumber(), slot.getFloorNumber(),
                slot.getLocation(), slot.getZone(), slot.getVehicleType(),
                slot.getHourlyPrice(), slot.getDailyPrice(), slot.getMonthlyPrice(),
                slot.getStatus(), slot.isAvailable()
            );
        }
    }
}
