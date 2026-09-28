package hu.boltseged.shipment;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.boltseged.account.Account;
import hu.boltseged.label.LabelStorage;
import hu.boltseged.label.ShipmentLabelRepository;
import hu.boltseged.shipping.ShippingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentCustomsWeightTest {
  @Test void rejectsCustomsGrossWeightAboveTotalPackageWeightBeforeCallingDhl() {
    ShipmentRepository shipments = mock(ShipmentRepository.class);
    ShippingProvider dhl = mock(ShippingProvider.class);
    ShipmentService service = new ShipmentService(shipments, mock(ShipmentLabelRepository.class), dhl, mock(LabelStorage.class), new ObjectMapper());
    Account account = account();
    when(shipments.findByAccountIdAndIdempotencyKey(account.getId(), "key")).thenReturn(java.util.Optional.empty());

    ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.create(account, "key", request(new BigDecimal("1.1"), BigDecimal.ONE)));

    assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    assertEquals("A vámáruk összes bruttó tömege nem lehet nagyobb a csomagok teljes súlyánál.", error.getReason());
    verifyNoInteractions(dhl);
  }

  @Test void requiresBothWeightsAndGrossAtLeastNet() {
    ShipmentController.ExportLineItemRequest missingGross = item(BigDecimal.ONE, null);
    ShipmentController.ExportLineItemRequest smallerGross = item(BigDecimal.ONE, new BigDecimal("0.8"));

    assertFalse(missingGross.isGrossWeightValid());
    assertFalse(smallerGross.isGrossWeightValid());
    assertTrue(item(new BigDecimal("0.8"), BigDecimal.ONE).isGrossWeightValid());
  }

  private Account account() {
    Account account = new Account("Sender", "sender@example.com", "hash", "CUSTOMER");
    account.update("Sender", null, null, "Contact", "sender@example.com", "FIXED", BigDecimal.ZERO);
    account.updateCompanyProfile("Sender", null, null, "Contact", "HU", "1051", "Budapest", "Street 1", null, null, "+361", null, null, null, null, null, null, null, null, null, null);
    return account;
  }

  private ShipmentController.CreateRequest request(BigDecimal grossWeight, BigDecimal packageWeight) {
    ShipmentController.AddressRequest recipient = new ShipmentController.AddressRequest("Recipient", "Recipient", "US", "94703", "BERKELEY", "Street 1", null, "CA", "+15105550123", "recipient@example.com");
    return new ShipmentController.CreateRequest("P", LocalDate.now(), LocalDateTime.of(2026, 9, 21, 10, 0), true, "Gift", BigDecimal.ONE, "USD", null, "USD", "DAP", new ShipmentController.ExportDeclarationRequest(List.of(item(new BigDecimal("0.8"), grossWeight)), LocalDate.now()), null, recipient, List.of(new ShipmentController.PackageRequest(packageWeight, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)), List.of());
  }

  private ShipmentController.ExportLineItemRequest item(BigDecimal netWeight, BigDecimal grossWeight) {
    return new ShipmentController.ExportLineItemRequest(1, "Wooden gift", BigDecimal.TEN, 1, "PCS", "HU", netWeight, grossWeight, "COMMERCIAL_PURPOSE_OR_SALE", "442019");
  }
}
