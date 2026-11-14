package com.smartpark.controller;

import com.smartpark.service.LocationSearchService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
@Validated
@PreAuthorize("hasRole('USER')")
public class LocationSearchController {

    private final LocationSearchService locationSearchService;

    public LocationSearchController(LocationSearchService locationSearchService) {
        this.locationSearchService = locationSearchService;
    }

    @GetMapping("/search")
    public List<LocationSearchService.LocationResult> search(
            @RequestParam @NotBlank @Size(min = 3, max = 120) String query) {
        return locationSearchService.search(query);
    }
}
