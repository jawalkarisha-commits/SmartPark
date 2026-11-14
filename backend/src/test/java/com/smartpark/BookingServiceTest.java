package com.smartpark;

import com.smartpark.entity.Booking;
import com.smartpark.entity.BookingStatus;
import com.smartpark.entity.ParkingSlot;
import com.smartpark.entity.ParkingFacility;
import com.smartpark.entity.User;
import com.smartpark.entity.Vehicle;
import com.smartpark.repository.BookingRepository;
import com.smartpark.repository.BookingGroupRepository;
import com.smartpark.repository.ParkingFacilityRepository;
import com.smartpark.repository.ParkingSlotRepository;
import com.smartpark.repository.UserRepository;
import com.smartpark.repository.VehicleRepository;
import com.smartpark.service.BookingService;
import com.smartpark.service.PricingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

class BookingServiceTest {

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final VehicleRepository vehicleRepository = mock(VehicleRepository.class);
    private final BookingGroupRepository bookingGroupRepository = mock(BookingGroupRepository.class);
    private final ParkingFacilityRepository facilityRepository = mock(ParkingFacilityRepository.class);
    private final ParkingSlotRepository parkingSlotRepository = mock(ParkingSlotRepository.class);
    private final PricingService pricingService = new PricingService();
    private final BookingService bookingService = new BookingService(
        bookingRepository, userRepository, vehicleRepository, pricingService,
        null, null, null, bookingGroupRepository, facilityRepository, parkingSlotRepository
    );

    private User user;
    private Vehicle vehicle;
    private ParkingSlot slot;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        vehicle = mock(Vehicle.class);
        slot = mock(ParkingSlot.class);
        startTime = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.MINUTES);
        endTime = startTime.plusHours(2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(vehicleRepository.findById(2L)).thenReturn(Optional.of(vehicle));
        when(vehicle.getOwner()).thenReturn(user);
        when(user.getId()).thenReturn(1L);
        when(bookingRepository.lockSlotById(3L)).thenReturn(Optional.of(slot));
        when(slot.isAvailable()).thenReturn(true);
        when(slot.getHourlyPrice()).thenReturn(new BigDecimal("12.00"));
        when(bookingRepository.existsOverlappingBooking(
                eq(3L), eq(List.of(
                    BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED,
                    BookingStatus.ACTIVE, BookingStatus.OVERDUE
                )), eq(startTime), eq(endTime), any(LocalDateTime.class)))
            .thenReturn(false);
        when(bookingRepository.save(any(Booking.class)))
            .thenAnswer(returnsFirstArg());
    }

    @Test
    void createsBookingForExistingUserVehicleAndSlot() {
        Booking booking = bookingService.createBooking(
            1L, 2L, 3L, startTime, endTime
        );

        assertEquals(user, booking.getUser());
        assertEquals(vehicle, booking.getVehicle());
        assertEquals(slot, booking.getParkingSlot());
        assertEquals(BookingStatus.PENDING_PAYMENT, booking.getStatus());
        assertTrue(booking.getHoldExpiresAt().isAfter(LocalDateTime.now()));
        assertEquals(new BigDecimal("24.00"), booking.getAmount());
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    void rejectsOverlappingBooking() {
        when(bookingRepository.existsOverlappingBooking(
                eq(3L), eq(List.of(
                    BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED,
                    BookingStatus.ACTIVE, BookingStatus.OVERDUE
                )), eq(startTime), eq(endTime), any(LocalDateTime.class)))
            .thenReturn(true);

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> bookingService.createBooking(1L, 2L, 3L, startTime, endTime)
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void rejectsInvalidTimeWindow() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> bookingService.createBooking(
                1L, 2L, 3L, LocalDateTime.now().minusMinutes(1), endTime
            )
        );

        assertEquals(400, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("Start time"));
        verify(userRepository, never()).findById(1L);
    }

    @Test
    void cancellationReleasesBookingStatus() {
        when(user.getId()).thenReturn(1L);
        Booking booking = new Booking(
            user, vehicle, slot, startTime, endTime,
            new BigDecimal("12.00"), new BigDecimal("24.00")
        );
        when(bookingRepository.findByIdForUpdate(4L))
            .thenReturn(Optional.of(booking));

        Booking cancelled = bookingService.cancelBooking(4L, 1L);

        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
    }

    @Test
    void expiresAnUnpaidHoldAndReleasesItsReservation() {
        LocalDateTime now = LocalDateTime.now();
        Booking booking = new Booking(
            user, vehicle, slot, startTime, endTime,
            new BigDecimal("12.00"), new BigDecimal("24.00")
        );
        booking.beginPaymentHold(now.minusMinutes(6));
        when(bookingRepository.findExpiredPaymentHolds(now)).thenReturn(List.of(booking));

        assertEquals(1, bookingService.expirePaymentHolds(now));
        assertEquals(BookingStatus.EXPIRED, booking.getStatus());
    }

    @Test
    void proratesLateFeeByActualOverstayMinutes() {
        Booking booking = new Booking(
            user, vehicle, slot, startTime, endTime,
            new BigDecimal("12.00"), new BigDecimal("24.00")
        );
        booking.start(startTime);
        booking.complete(endTime.plusMinutes(45));

        assertEquals(BookingStatus.COMPLETED, booking.getStatus());
        assertEquals(1, booking.getLateHours());
        assertEquals(new BigDecimal("9.00"), booking.getLateFeeAmount());
        assertEquals(endTime.plusMinutes(45), booking.getActualExitTime());
    }

    @Test
    void atomicallyAllocatesOneCompatibleSpacePerVehicleInAGroup() {
        Vehicle secondVehicle = mock(Vehicle.class);
        ParkingSlot secondSlot = mock(ParkingSlot.class);
        ParkingFacility facility = mock(ParkingFacility.class);
        var group = new com.smartpark.entity.BookingGroup(user);
        when(vehicleRepository.findById(5L)).thenReturn(Optional.of(secondVehicle));
        when(secondVehicle.getOwner()).thenReturn(user);
        when(secondVehicle.getVehicleType()).thenReturn("Four-wheeler");
        when(vehicle.getVehicleType()).thenReturn("Four-wheeler");
        when(facilityRepository.findById(9L)).thenReturn(Optional.of(facility));
        when(facility.isActive()).thenReturn(true);
        when(facility.getId()).thenReturn(9L);
        when(slot.getFacility()).thenReturn(facility);
        when(secondSlot.getFacility()).thenReturn(facility);
        when(secondSlot.getId()).thenReturn(4L);
        when(slot.getId()).thenReturn(3L);
        when(secondSlot.isAvailable()).thenReturn(true);
        when(secondSlot.getHourlyPrice()).thenReturn(new BigDecimal("12.00"));
        when(parkingSlotRepository.findAvailableSlots(
                "Four-wheeler", List.of(
                    BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED,
                    BookingStatus.ACTIVE, BookingStatus.OVERDUE
                ), startTime, endTime))
            .thenReturn(List.of(slot, secondSlot));
        when(bookingRepository.lockSlotById(4L)).thenReturn(Optional.of(secondSlot));
        when(bookingRepository.existsOverlappingBooking(
                org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.eq(startTime),
                org.mockito.ArgumentMatchers.eq(endTime),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
            .thenReturn(false);
        when(bookingGroupRepository.save(any())).thenReturn(group);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(returnsFirstArg());

        var reservation = bookingService.createBookingGroup(
            1L, 9L, List.of(2L, 5L), startTime, endTime,
            com.smartpark.entity.BillingPeriod.HOURLY
        );

        assertEquals(2, reservation.bookings().size());
        assertEquals(BookingStatus.PENDING_PAYMENT, reservation.bookings().get(0).getStatus());
        assertEquals(BookingStatus.PENDING_PAYMENT, reservation.bookings().get(1).getStatus());
        assertEquals(reservation.group(), reservation.bookings().get(0).getBookingGroup());
        assertEquals(reservation.group(), reservation.bookings().get(1).getBookingGroup());
        assertEquals(2, reservation.bookings().stream()
            .map(booking -> booking.getParkingSlot().getId()).distinct().count());
    }

    @Test
    void estimatesGroupBookingUsingParkingSlotRepository() {
        Vehicle secondVehicle = mock(Vehicle.class);
        ParkingSlot secondSlot = mock(ParkingSlot.class);
        ParkingFacility facility = mock(ParkingFacility.class);
        when(vehicleRepository.findById(5L)).thenReturn(Optional.of(secondVehicle));
        when(vehicle.getVehicleType()).thenReturn("Four-wheeler");
        when(secondVehicle.getOwner()).thenReturn(user);
        when(secondVehicle.getVehicleType()).thenReturn("Four-wheeler");
        when(vehicle.getVehicleNumber()).thenReturn("MH1234");
        when(secondVehicle.getId()).thenReturn(5L);
        when(secondVehicle.getVehicleNumber()).thenReturn("MH5678");
        when(facilityRepository.findById(9L)).thenReturn(Optional.of(facility));
        when(facility.isActive()).thenReturn(true);
        when(facility.getId()).thenReturn(9L);
        when(slot.getFacility()).thenReturn(facility);
        when(secondSlot.getFacility()).thenReturn(facility);
        when(slot.getId()).thenReturn(3L);
        when(slot.getSlotNumber()).thenReturn("A1");
        when(slot.getHourlyPrice()).thenReturn(new BigDecimal("12.00"));
        when(secondSlot.getId()).thenReturn(4L);
        when(secondSlot.getSlotNumber()).thenReturn("A2");
        when(secondSlot.getHourlyPrice()).thenReturn(new BigDecimal("15.00"));
        when(parkingSlotRepository.findAvailableSlots(
                eq("Four-wheeler"), any(), eq(startTime), eq(endTime)))
            .thenReturn(List.of(slot, secondSlot));

        var estimate = bookingService.estimateBookingGroup(
            1L, 9L, List.of(2L, 5L), startTime, endTime,
            com.smartpark.entity.BillingPeriod.HOURLY
        );

        assertEquals(2, estimate.vehicles().size());
        assertEquals(new BigDecimal("54.00"), estimate.totalAmount());
        verify(parkingSlotRepository, org.mockito.Mockito.times(2))
            .findAvailableSlots(eq("Four-wheeler"), any(), eq(startTime), eq(endTime));
    }
}
