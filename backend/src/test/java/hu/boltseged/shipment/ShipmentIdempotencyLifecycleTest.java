package hu.boltseged.shipment;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.boltseged.account.Account;
import hu.boltseged.label.LabelStorage;
import hu.boltseged.label.ShipmentLabelRepository;
import hu.boltseged.shipping.DhlApiException;
import hu.boltseged.shipping.ShippingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShipmentIdempotencyLifecycleTest {
  @Test void failedKeyIsReusedButNewKeyCreatesANewDhlAttempt() {
    ShipmentRepository shipments = mock(ShipmentRepository.class);
    ShippingProvider dhl = mock(ShippingProvider.class);
    ShipmentService service = new ShipmentService(shipments, mock(ShipmentLabelRepository.class), dhl, mock(LabelStorage.class), new ObjectMapper());
    Account account = account();
    Shipment failed = new Shipment(account, "key-a", "P", LocalDate.now(), "{}", "{}", List.of(new Shipment.PackageInput(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)));
    failed.fail("Original DHL failure");
    Shipment[] newAttempt = new Shipment[1];
    when(shipments.findByAccountIdAndIdempotencyKey(eq(account.getId()), anyString())).thenAnswer(invocation ->
        "key-a".equals(invocation.getArgument(1)) ? Optional.of(failed) : Optional.empty());
    when(shipments.saveAndFlush(any())).thenAnswer(invocation -> {
      newAttempt[0] = invocation.getArgument(0);
      return newAttempt[0];
    });
    when(shipments.findById(any())).thenAnswer(invocation -> Optional.of(newAttempt[0]));
    when(shipments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(dhl.quote(any())).thenReturn(List.of(new ShippingProvider.Quote("P", "Express", BigDecimal.TEN, "HUF", null)));
    when(dhl.create(any())).thenThrow(new DhlApiException(HttpStatus.BAD_GATEWAY, List.of("DHL unavailable")));

    assertSame(failed, service.create(account, "key-a", request()));
    verifyNoInteractions(dhl);

    assertThrows(DhlApiException.class, () -> service.create(account, "key-b", request()));
    assertNotNull(newAttempt[0]);
    assertNotEquals(failed.getId(), newAttempt[0].getId());
    verify(dhl, times(1)).create(any());
  }

  @Test void rejectsDescriptionLongerThanSeventyCharactersBeforeDhl() {
    ShipmentRepository shipments = mock(ShipmentRepository.class);
    ShippingProvider dhl = mock(ShippingProvider.class);
    ShipmentService service = new ShipmentService(shipments, mock(ShipmentLabelRepository.class), dhl, mock(LabelStorage.class), new ObjectMapper());
    Account account = account();
    when(shipments.findByAccountIdAndIdempotencyKey(account.getId(), "key")).thenReturn(Optional.empty());
    ShipmentController.AddressRequest recipient = new ShipmentController.AddressRequest("Recipient", "Recipient", "DE", "10115", "Berlin", "Street 1", null, null, "+493012345", "recipient@example.com");
    ShipmentController.CreateRequest request = new ShipmentController.CreateRequest("P", LocalDate.now(), LocalDateTime.of(2026, 9, 21, 10, 0), true, "x".repeat(71), BigDecimal.TEN, "HUF", null, "USD", "DAP", null, null, recipient, List.of(new ShipmentController.PackageRequest(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)), List.of());

    org.springframework.web.server.ResponseStatusException error = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.create(account, "key", request));

    assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    assertEquals("A küldemény tartalmának leírása legfeljebb 70 karakter lehet.", error.getReason());
    verifyNoInteractions(dhl);
  }

  private Account account() {
    Account account = new Account("Sender", "sender@example.com", "hash", "CUSTOMER");
    account.update("Sender", null, null, "Contact", "sender@example.com", "FIXED", BigDecimal.ZERO);
    account.updateCompanyProfile("Sender", null, null, "Contact", "HU", "1051", "Budapest", "Street 1", null, null, "+361", null, null, null, null, null, null, null, null, null, null);
    return account;
  }

  private ShipmentController.CreateRequest request() {
    ShipmentController.AddressRequest recipient = new ShipmentController.AddressRequest("Recipient", "Recipient", "DE", "10115", "Berlin", "Street 1", null, null, "+493012345", "recipient@example.com");
    return new ShipmentController.CreateRequest("P", LocalDate.now(), LocalDateTime.of(2026, 9, 21, 10, 0), false, "Shipment", BigDecimal.TEN, "HUF", null, null, null, null, null, recipient, List.of(new ShipmentController.PackageRequest(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)), List.of());
  }
}
