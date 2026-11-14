package com.smartpark.repository;

import com.smartpark.entity.CustomerRegistration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface CustomerRegistrationRepository
        extends JpaRepository<CustomerRegistration, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CustomerRegistration> findByEmailIgnoreCase(String email);
}
