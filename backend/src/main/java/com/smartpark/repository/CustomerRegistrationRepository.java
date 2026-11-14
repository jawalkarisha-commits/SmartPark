package com.smartpark.repository;

import com.smartpark.entity.CustomerRegistration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRegistrationRepository
        extends JpaRepository<CustomerRegistration, Long> {

    @Query(
        value = """
            SELECT * FROM customer_registrations
            WHERE LOWER(email) = LOWER(:email)
            FOR UPDATE
            """,
        nativeQuery = true
    )
    Optional<CustomerRegistration> findByEmailIgnoreCase(@Param("email") String email);
}
