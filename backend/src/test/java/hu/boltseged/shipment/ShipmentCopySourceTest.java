package hu.boltseged.shipment;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.boltseged.account.Account;
import hu.boltseged.label.LabelStorage;
import hu.boltseged.label.ShipmentLabelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShipmentCopySourceTest {
  @Test
  void returnsOnlyReusableDataForTheAuthenticatedOwnersShipment() {
    Account owner = new Account("Owner", "owner@example.com", "hash", "CUSTOMER");
    Shipment shipment = new Shipment(owner, "key", "P", LocalDate.now(), "{}",
        "{\"companyName\":\"Recipient\",\"countryCode\":\"US\"}",
        List.of(new Shipment.PackageInput(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.valueOf(20), BigDecimal.valueOf(30))));
    shipment.setCustoms("{\"customsDeclarable\":true,\"description\":\"Gift\",\"declaredValueCurrency\":\"USD\",\"incoterm\":\"DAP\",\"lineItems\":[{\"description\":\"Wooden gift\",\"quantity\":2,\"quantityUnitOfMeasurement\":\"PCS\",\"price\":50,\"manufacturerCountry\":\"HU\",\"commodityCode\":\"442019\",\"netWeight\":0.8,\"grossWeight\":1,\"exportReasonType\":\"COMMERCIAL_PURPOSE_OR_SALE\"}]}");
    ShipmentRepository repository = mock(ShipmentRepository.class);
    when(repository.findDetailedById(shipment.getId())).thenReturn(Optional.of(shipment));
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(owner, null));

    ShipmentController controller = new ShipmentController(repository, mock(ShipmentService.class), mock(ShipmentLabelRepository.class), mock(LabelStorage.class), new ObjectMapper());
    ShipmentController.CopySource source = controller.copySource(shipment.getId());

    assertEquals("P", source.productCode());
    assertEquals("Recipient", source.recipient().path("companyName").asText());
    assertEquals(1, source.packages().size());
    assertEquals("DAP", source.customs().incoterm());
    assertEquals("Wooden gift", source.customs().lineItems().getFirst().description());
    assertFalse(List.of(ShipmentController.CopySource.class.getRecordComponents()).stream().anyMatch(component -> component.getName().contains("Price") || component.getName().contains("tracking")));
  }

  @Test
  void rejectsAnotherAccountsShipment() {
    Account owner = new Account("Owner", "owner@example.com", "hash", "CUSTOMER");
    Account other = new Account("Other", "other@example.com", "hash", "CUSTOMER");
    Shipment shipment = new Shipment(owner, "key", "P", LocalDate.now(), "{}", "{}", List.of(new Shipment.PackageInput(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)));
    ShipmentRepository repository = mock(ShipmentRepository.class);
    when(repository.findDetailedById(shipment.getId())).thenReturn(Optional.of(shipment));
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(other, null));

    ShipmentController controller = new ShipmentController(repository, mock(ShipmentService.class), mock(ShipmentLabelRepository.class), mock(LabelStorage.class), new ObjectMapper());

    assertThrows(ResponseStatusException.class, () -> controller.copySource(shipment.getId()));
  }
}
