
package com.smartpark.controller;

import com.smartpark.entity.ParkingSlot;
import com.smartpark.repository.ParkingSlotRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parking-slots")
@CrossOrigin(origins = "*")
public class ParkingSlotController {

    private final ParkingSlotRepository repository;

    public ParkingSlotController(ParkingSlotRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<ParkingSlot> getAllSlots() {
        return repository.findAll();
    }

    @PostMapping
    public ParkingSlot addSlot(@RequestBody ParkingSlot slot) {
        return repository.save(slot);
    }

    @DeleteMapping("/{id}")
    public String deleteSlot(@PathVariable Long id) {
        repository.deleteById(id);
        return "Parking slot deleted successfully";
    }
}
