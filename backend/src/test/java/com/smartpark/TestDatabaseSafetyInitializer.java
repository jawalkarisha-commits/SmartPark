package com.smartpark;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

public class TestDatabaseSafetyInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        String url = environment.getProperty("spring.datasource.url", "");
        String driver = environment.getProperty("spring.datasource.driver-class-name", "");
        boolean flywayEnabled = environment.getProperty(
            "spring.flyway.enabled", Boolean.class, true
        );

        if (!environment.matchesProfiles("test")
                || !url.startsWith("jdbc:h2:mem:")
                || !"org.h2.Driver".equals(driver)
                || flywayEnabled) {
            throw new IllegalStateException(
                "Tests require the test profile, an in-memory H2 datasource, "
                    + "and Flyway disabled"
            );
        }
    }
}
