package hu.boltseged.shipment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipmentInvoiceNumberTest {
  @Test
  void generatesStableDhlSafeInvoiceNumbersFromIdempotencyKeys() {
    String key = "550e8400-e29b-41d4-a716-446655440000";
    String invoiceNumber = ShipmentService.invoiceNumber(key);

    assertEquals("BS-550e8400e29b41d4a716446655440000", invoiceNumber);
    assertTrue(invoiceNumber.length() <= 35);
    assertEquals(invoiceNumber, ShipmentService.invoiceNumber(key));
    assertNotEquals(
        invoiceNumber,
        ShipmentService.invoiceNumber("550e8400-e29b-41d4-a716-446655440001"));
  }
}
