package com.smartpark.controller;

import com.smartpark.entity.Vehicle;
import com.smartpark.repository.VehicleRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@CrossOrigin(origins = "*")
public class VehicleController {

    private final VehicleRepository repository;

    public VehicleController(VehicleRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Vehicle> getAllVehicles() {
        return repository.findAll();
    }

    @PostMapping
    public Vehicle registerVehicle(@RequestBody Vehicle vehicle) {
        return repository.save(vehicle);
    }

    @DeleteMapping("/{id}")
    public String deleteVehicle(@PathVariable Long id) {
        repository.deleteById(id);
        return "Vehicle deleted successfully";
    }
}