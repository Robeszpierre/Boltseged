package hu.boltseged.pickup;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.boltseged.shipment.ShipmentRepository;
import hu.boltseged.shipping.ShippingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class PickupServiceWiringTest {
  @Test
  void springConstructsPickupServiceUsingTheProductionConstructor() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(PickupBookingRepository.class, () -> mock(PickupBookingRepository.class));
      context.registerBean(ShipmentRepository.class, () -> mock(ShipmentRepository.class));
      context.registerBean(ShippingProvider.class, () -> mock(ShippingProvider.class));
      context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
      context.register(PickupService.class);
      context.refresh();

      assertNotNull(context.getBean(PickupService.class));
    }
  }
}
