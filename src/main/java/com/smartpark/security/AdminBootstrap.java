package com.smartpark.security;

import com.smartpark.entity.User;
import com.smartpark.repository.UserRepository;
import com.smartpark.security.PasswordPolicy;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "smartpark.admin.bootstrap.email")
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${smartpark.admin.bootstrap.email}") String email,
            @Value("${smartpark.admin.bootstrap.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            throw new IllegalStateException(
                "SMARTPARK_ADMIN_BOOTSTRAP_PASSWORD is required when "
                    + "SMARTPARK_ADMIN_BOOTSTRAP_EMAIL is configured"
            );
        }
        PasswordPolicy.validate(password);

        User user = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            User newUser = new User();
            newUser.setFullName("SmartPark Administrator");
            newUser.setEmail(email);
            newUser.setPasswordHash(passwordEncoder.encode(password));
            return newUser;
        });
        user.setRole(UserRole.ADMIN.name());
        user.setEnabled(true);
        userRepository.save(user);
    }
}
