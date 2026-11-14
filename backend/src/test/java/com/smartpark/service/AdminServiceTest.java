package com.smartpark.service;

import com.smartpark.entity.User;
import com.smartpark.repository.BookingRepository;
import com.smartpark.repository.PaymentRepository;
import com.smartpark.repository.ParkingSlotRepository;
import com.smartpark.repository.SmartParkSettingsRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.repository.VehicleRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ParkingSlotRepository slotRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SmartParkSettingsRepository settingsRepository;
    @Mock
    private RazorpayPaymentService paymentService;
    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock
    private OwnerVerificationService ownerVerificationService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
            bookingRepository, slotRepository, userRepository, vehicleRepository,
            paymentRepository, settingsRepository, paymentService, jdbcTemplate,
            ownerVerificationService
        );
    }

    @Test
    void cannotDeleteTheLastEnabledAdministrator() {
        User lastAdmin = new User();
        lastAdmin.setRole("ADMIN");
        lastAdmin.setEnabled(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(lastAdmin));
        when(userRepository.countByRoleAndEnabledTrue("ADMIN")).thenReturn(1L);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> adminService.deleteUser(10L, 20L)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(userRepository).countByRoleAndEnabledTrue("ADMIN");
        verifyNoInteractions(jdbcTemplate, ownerVerificationService);
    }
}
