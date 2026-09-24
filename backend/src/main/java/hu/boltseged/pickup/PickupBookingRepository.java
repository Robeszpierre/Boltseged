package hu.boltseged.pickup;
import org.springframework.data.jpa.repository.*; import java.util.*;
public interface PickupBookingRepository extends JpaRepository<PickupBooking,UUID>{List<PickupBooking> findByAccountIdOrderByCreatedAtDesc(UUID accountId);Optional<PickupBooking> findByIdAndAccountId(UUID id,UUID accountId);boolean existsByStatusAndShipments_Id(String status,UUID shipmentId);}
