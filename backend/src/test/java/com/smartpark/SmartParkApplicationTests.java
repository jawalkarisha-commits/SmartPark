package com.smartpark;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = "smartpark.jwt.secret=test-secret-key-for-jwt-signing-32-bytes-minimum")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestDatabaseSafetyInitializer.class)
class SmartParkApplicationTests {

	@Test
	void contextLoads() {
	}

}
