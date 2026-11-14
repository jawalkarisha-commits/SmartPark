package com.smartpark.controller;

import com.smartpark.service.SmartParkSettingsService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class PublicSettingsController {

    private final SmartParkSettingsService settingsService;

    public PublicSettingsController(SmartParkSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/vehicle-types")
    public List<String> getVehicleTypes() {
        return settingsService.getVehicleTypes();
    }
}
