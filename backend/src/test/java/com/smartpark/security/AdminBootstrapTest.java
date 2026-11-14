package com.smartpark.security;

import com.smartpark.entity.User;
import com.smartpark.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    @Test
    void updatesExistingAdminWithBcryptHash() {
        UserRepository userRepository = mock(UserRepository.class);
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
        User existing = existingUser();
        when(userRepository.findByEmailIgnoreCase("admin@smartpark01"))
            .thenReturn(Optional.of(existing));

        AdminBootstrap bootstrap = new AdminBootstrap(
            userRepository,
            passwordEncoder,
            "admin@smartpark01",
            "StrongPass1",
            true
        );
        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        assertNotEquals("StrongPass1", existing.getPasswordHash());
        assertTrue(existing.getPasswordHash().startsWith("$2a$12$"));
        assertTrue(passwordEncoder.matches("StrongPass1", existing.getPasswordHash()));
        assertEquals("ADMIN", existing.getRole());
        assertTrue(existing.isEnabled());
        verify(userRepository).save(existing);
    }

    @Test
    void preservesExistingPasswordUnlessResetIsExplicitlyEnabled() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        User existing = existingUser();
        when(userRepository.findByEmailIgnoreCase("admin@smartpark01"))
            .thenReturn(Optional.of(existing));

        AdminBootstrap bootstrap = new AdminBootstrap(
            userRepository,
            passwordEncoder,
            "admin@smartpark01",
            "StrongPass1",
            false
        );
        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        assertEquals("old-hash", existing.getPasswordHash());
        assertEquals("ADMIN", existing.getRole());
        assertTrue(existing.isEnabled());
        verify(passwordEncoder, never()).encode("StrongPass1");
        verify(userRepository).save(existing);
    }

    @Test
    void createsAdminWithEncodedPasswordWhenAccountDoesNotExist() {
        UserRepository userRepository = mock(UserRepository.class);
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);
        when(userRepository.findByEmailIgnoreCase("admin@smartpark01"))
            .thenReturn(Optional.empty());

        AdminBootstrap bootstrap = new AdminBootstrap(
            userRepository,
            passwordEncoder,
            "admin@smartpark01",
            "StrongPass1",
            false
        );
        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        User created = savedUser.getValue();
        assertEquals("admin@smartpark01", created.getEmail());
        assertEquals("ADMIN", created.getRole());
        assertNotEquals("StrongPass1", created.getPasswordHash());
        assertTrue(passwordEncoder.matches("StrongPass1", created.getPasswordHash()));
    }

    private static User existingUser() {
        User existing = new User();
        existing.setEmail("admin@smartpark01");
        existing.setPasswordHash("old-hash");
        existing.setRole("USER");
        existing.setEnabled(false);
        return existing;
    }
}
