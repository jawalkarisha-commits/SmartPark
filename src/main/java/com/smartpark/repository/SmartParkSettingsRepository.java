package com.smartpark.repository;

import com.smartpark.entity.SmartParkSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SmartParkSettingsRepository extends JpaRepository<SmartParkSettings, Integer> {
}
