package hu.boltseged.shipping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DhlRatePayloadTest {
  private final DhlExpressShippingProvider provider = provider();

  @Test
  void addsDutyTaxesAccountAndDdServiceForDdpRateOnly() {
    JsonNode ddp = provider.ratePayload(request("DDP"));
    JsonNode dap = provider.ratePayload(request("DAP"));

    assertEquals(2, ddp.path("accounts").size());
    assertEquals("shipper", ddp.path("accounts").get(0).path("typeCode").asText());
    assertEquals("duties-taxes", ddp.path("accounts").get(1).path("typeCode").asText());
    assertEquals("123456789", ddp.path("accounts").get(1).path("number").asText());
    assertEquals(1, ddp.path("valueAddedServices").size());
    assertEquals("DD", ddp.path("valueAddedServices").get(0).path("serviceCode").asText());
    assertEquals(1, dap.path("accounts").size());
    assertFalse(dap.has("valueAddedServices"));
    assertFalse(ddp.toString().contains("GGP"));
  }

  private DhlExpressShippingProvider provider() {
    DhlExpressShippingProvider.DhlProperties properties = new DhlExpressShippingProvider.DhlProperties();
    properties.setApiBaseUrl("http://localhost");
    properties.setUsername("user");
    properties.setPassword("secret");
    properties.setAccountNumber("123456789");
    return new DhlExpressShippingProvider(properties, new ObjectMapper());
  }

  private ShippingProvider.QuoteRequest request(String incoterm) {
    ShippingProvider.Address address = new ShippingProvider.Address("Example Kft.", "Example User", "HU", "8200", "Veszprem", "Example street 1", null, null, "+36201234567", "example@example.com");
    return new ShippingProvider.QuoteRequest(address, address, LocalDateTime.of(2026, 9, 21, 10, 0), true, incoterm,
        List.of(new ShippingProvider.Package(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN),
            new ShippingProvider.Package(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN)));
  }
}
