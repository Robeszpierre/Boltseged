package hu.boltseged.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DhlShipmentPayloadTest {
  private final DhlExpressShippingProvider provider = provider();

  @Test
  void addsPaperlessTradeExactlyOnceForEveryCustomsShipment() {
    JsonNode withoutDocuments = provider.shipmentPayload(customsRequest(List.of()));
    JsonNode withDocuments = provider.shipmentPayload(customsRequest(
        List.of(new ShippingProvider.CustomsDocument("CIN", "PDF", "base64-content"))));
    JsonNode nonCustoms = provider.shipmentPayload(nonCustomsRequest());

    assertEquals(1, withoutDocuments.path("valueAddedServices").size());
    assertEquals("WY", withoutDocuments.path("valueAddedServices").get(0).path("serviceCode").asText());
    assertEquals(1, withDocuments.path("valueAddedServices").size());
    assertEquals("WY", withDocuments.path("valueAddedServices").get(0).path("serviceCode").asText());
    assertTrue(withDocuments.has("documentImages"));
    assertFalse(nonCustoms.has("valueAddedServices"));
  }

  @Test
  void sumsNetAndGrossWeightsAcrossAllCustomsLineItems() {
    JsonNode declaration = provider.shipmentPayload(customsRequest(List.of()))
        .path("content").path("exportDeclaration");

    assertEquals(2, declaration.path("lineItems").size());
    assertEquals(0, new BigDecimal("2.0").compareTo(declaration.path("totalNetWeight").decimalValue()));
    assertEquals(0, new BigDecimal("2.5").compareTo(declaration.path("totalGrossWeight").decimalValue()));
  }

  private DhlExpressShippingProvider provider() {
    DhlExpressShippingProvider.DhlProperties properties = new DhlExpressShippingProvider.DhlProperties();
    properties.setApiBaseUrl("http://localhost");
    properties.setUsername("user");
    properties.setPassword("secret");
    properties.setAccountNumber("123456789");
    return new DhlExpressShippingProvider(properties, new ObjectMapper());
  }

  private ShippingProvider.CreateRequest customsRequest(List<ShippingProvider.CustomsDocument> documents) {
    return new ShippingProvider.CreateRequest("P", address(), address(), LocalDateTime.of(2026, 9, 21, 10, 0), true, "Wooden gift", new BigDecimal("50"), "USD", "DAP", declaration(), List.of(parcel()), documents);
  }

  private ShippingProvider.CreateRequest nonCustomsRequest() {
    return new ShippingProvider.CreateRequest("P", address(), address(), LocalDateTime.of(2026, 9, 21, 10, 0), false, "Shipment", null, null, null, null, List.of(parcel()), List.of());
  }

  private ShippingProvider.Address address() {
    return new ShippingProvider.Address("Example Kft.", "Example User", "HU", "8200", "Veszprem", "Example street 1", null, null, "+36201234567", "example@example.com");
  }

  private ShippingProvider.Package parcel() {
    return new ShippingProvider.Package(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN);
  }

  private ShippingProvider.ExportDeclaration declaration() {
    return new ShippingProvider.ExportDeclaration(List.of(
        new ShippingProvider.ExportLineItem(1, "Wooden gift", new BigDecimal("50"), 1, "PCS", "HU", new BigDecimal("0.8"), BigDecimal.ONE, "COMMERCIAL_PURPOSE_OR_SALE", "442019"),
        new ShippingProvider.ExportLineItem(2, "Wooden ornament", new BigDecimal("25"), 1, "PCS", "HU", new BigDecimal("1.2"), new BigDecimal("1.5"), "COMMERCIAL_PURPOSE_OR_SALE", "442019")), "BS-550e8400e29b41d4a716446655440000", LocalDate.of(2026, 9, 21));
  }
}
