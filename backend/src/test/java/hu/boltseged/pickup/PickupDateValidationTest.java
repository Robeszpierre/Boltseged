package hu.boltseged.pickup;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.boltseged.account.Account;
import hu.boltseged.shipment.Shipment;
import hu.boltseged.shipment.ShipmentRepository;
import hu.boltseged.shipping.ShippingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PickupDateValidationTest {
  private static final ZoneId BUDAPEST = ZoneId.of("Europe/Budapest");
  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T12:10:00Z"), BUDAPEST);
  private final PickupBookingRepository bookings = mock(PickupBookingRepository.class);
  private final ShipmentRepository shipments = mock(ShipmentRepository.class);
  private final ShippingProvider dhl = mock(ShippingProvider.class);
  private final PickupService service = new PickupService(bookings, shipments, dhl, new ObjectMapper(), CLOCK);

  @Test
  void acceptsTodayAndTomorrowButRejectsYesterday() {
    LocalDate today = LocalDate.now(BUDAPEST);

    assertTrue(request(today, LocalTime.of(14, 15), LocalTime.of(15, 0)).isDateRangeValid());
    assertTrue(request(today.plusDays(1), LocalTime.of(13, 0), LocalTime.of(17, 0)).isDateRangeValid());
    assertTrue(request(today.plusDays(11), LocalTime.of(13, 0), LocalTime.of(17, 0)).isDateRangeValid());
    assertFalse(request(today.minusDays(1), LocalTime.of(13, 0), LocalTime.of(17, 0)).isDateRangeValid());
  }

  @Test
  void acceptsAFutureSameDayWindow() {
    Account account = account();
    Shipment shipment = shipment(account);
    when(shipments.findAllForPickupByIdIn(any())).thenReturn(List.of(shipment));
    when(dhl.createPickup(any())).thenReturn(new ShippingProvider.PickupResult(List.of("CONFIRM"), "14:15", "2026-10-04", List.of()));
    when(bookings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service.create(account, request(LocalDate.now(CLOCK), LocalTime.of(14, 15), LocalTime.of(15, 0)));

    verify(dhl).createPickup(argThat(pickup -> pickup.plannedPickupDateAndTime().equals(LocalDateTime.of(2026, 10, 4, 14, 15))));
  }

  @Test
  void rejectsSameDayWindowThatHasAlreadyEnded() {
    ResponseStatusException error = assertThrows(ResponseStatusException.class,
        () -> service.create(account(), request(LocalDate.now(CLOCK), LocalTime.of(13, 0), LocalTime.of(14, 0))));

    assertEquals("A mai futárfelvételhez jövőbeli időpontot válassz.", error.getReason());
    verifyNoInteractions(dhl);
  }

  @Test
  void rejectsPastPickupDateBeforeCallingDhl() {
    ResponseStatusException error = assertThrows(ResponseStatusException.class,
        () -> service.create(account(), request(LocalDate.now(CLOCK).minusDays(1), LocalTime.of(13, 0), LocalTime.of(17, 0))));

    assertEquals("A futárfelvétel dátuma nem lehet korábbi a mai napnál.", error.getReason());
    verifyNoInteractions(dhl);
  }

  private PickupController.CreateRequest request(LocalDate date, LocalTime ready, LocalTime close) {
    return new PickupController.CreateRequest(List.of(UUID.randomUUID()), date, ready, close, null, "business", null, null);
  }

  private Account account() {
    Account account = new Account("Sender", "customer@example.com", "hash", "CUSTOMER");
    account.updateCompanyProfile("Sender", null, null, "Sender contact", "HU", "1051", "Budapest", "Sender Street", null, null, "+361",
        "Pickup", "Pickup contact", "HU", "8200", "Veszprem", "Pickup Street", null, null, "+36201234567", "pickup@example.com");
    return account;
  }

  private Shipment shipment(Account account) {
    Shipment shipment = new Shipment(account, "key", "P", LocalDate.of(2026, 10, 4),
        "{\"companyName\":\"Sender\",\"contactName\":\"Sender contact\",\"countryCode\":\"HU\",\"postalCode\":\"1051\",\"cityName\":\"Budapest\",\"addressLine1\":\"Sender Street\",\"phone\":\"+361\",\"email\":\"customer@example.com\"}",
        "{}", List.of(new Shipment.PackageInput(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN)));
    shipment.setCustoms("{\"customsDeclarable\":false}");
    shipment.complete("TRACK", "DHL", List.of("PIECE"), BigDecimal.ONE, "HUF");
    return shipment;
  }
}
