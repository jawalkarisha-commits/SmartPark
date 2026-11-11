package com.smartpark.repository;

import com.smartpark.entity.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    void deleteAllByUser_Id(Long userId);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}
