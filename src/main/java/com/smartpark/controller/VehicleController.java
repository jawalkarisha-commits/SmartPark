package com.smartpark.controller;

import com.smartpark.entity.Vehicle;
import com.smartpark.security.CurrentUser;
import com.smartpark.service.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/vehicles")
@PreAuthorize("hasAnyRole('USER', 'ADMIN')")
public class VehicleController {

    private final VehicleService vehicleService;

    public VehicleController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<VehicleResponse> getVehicles(
            @AuthenticationPrincipal CurrentUser currentUser) {
        return vehicleService.getVehiclesForUser(currentUser.id()).stream()
            .map(VehicleResponse::from)
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse createVehicle(
            @AuthenticationPrincipal CurrentUser currentUser,
            @Valid @RequestBody VehicleRequest request) {
        return VehicleResponse.from(vehicleService.createVehicle(
            currentUser.id(), request.vehicleNumber(), request.vehicleType(),
            request.brand(), request.model(), request.color()
        ));
    }

    @GetMapping("/{vehicleId}")
    public VehicleResponse getVehicle(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long vehicleId) {
        return VehicleResponse.from(
            vehicleService.getVehicle(vehicleId, currentUser.id())
        );
    }

    @PutMapping("/{vehicleId}")
    public VehicleResponse updateVehicle(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long vehicleId,
            @Valid @RequestBody VehicleRequest request) {
        return VehicleResponse.from(vehicleService.updateVehicle(
            vehicleId, currentUser.id(), request.vehicleNumber(),
            request.vehicleType(), request.brand(), request.model(), request.color()
        ));
    }

    @DeleteMapping("/{vehicleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVehicle(
            @AuthenticationPrincipal CurrentUser currentUser,
            @PathVariable Long vehicleId) {
        vehicleService.deleteVehicle(vehicleId, currentUser.id());
    }

    public record VehicleRequest(
        @NotBlank @Size(min = 3, max = 20)
        @Pattern(regexp = "^[A-Za-z0-9 -]+$") String vehicleNumber,
        @NotBlank @Size(max = 40) String vehicleType,
        @Size(max = 100) String brand,
        @Size(max = 100) String model,
        @Size(max = 80) String color
    ) {
    }

    public record VehicleResponse(
        Long id,
        String vehicleNumber,
        String vehicleType,
        String brand,
        String model,
        String color
    ) {
        static VehicleResponse from(Vehicle vehicle) {
            return new VehicleResponse(
                vehicle.getId(), vehicle.getVehicleNumber(),
                vehicle.getVehicleType(), vehicle.getBrand(),
                vehicle.getModel(), vehicle.getColor()
            );
        }
    }
}
