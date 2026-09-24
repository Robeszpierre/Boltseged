package hu.boltseged.pickup;

import hu.boltseged.account.Account;
import hu.boltseged.shipment.Shipment;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity @Table(name="pickup_bookings")
public class PickupBooking {
  @Id private UUID id;
  @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="account_id",nullable=false) private Account account;
  @JdbcTypeCode(SqlTypes.JSON) @Column(name="dispatch_confirmation_numbers",columnDefinition="jsonb") private String dispatchConfirmationNumbers;
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private String warnings;
  private LocalDate pickupDate; private LocalTime readyTime; private LocalTime closeTime;
  @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private String pickupAddress;
  private int packageCount; private BigDecimal totalWeight; private String status; private Instant createdAt;
  @ManyToMany @JoinTable(name="pickup_booking_shipments",joinColumns=@JoinColumn(name="pickup_booking_id"),inverseJoinColumns=@JoinColumn(name="shipment_id"))
  private List<Shipment> shipments=new ArrayList<>();
  protected PickupBooking(){}
  public PickupBooking(Account account,String confirmations,String warnings,LocalDate date,LocalTime ready,LocalTime close,String address,int count,BigDecimal weight,List<Shipment> shipments){id=UUID.randomUUID();this.account=account;dispatchConfirmationNumbers=confirmations;this.warnings=warnings;pickupDate=date;readyTime=ready;closeTime=close;pickupAddress=address;packageCount=count;totalWeight=weight;status="BOOKED";createdAt=Instant.now();this.shipments.addAll(shipments);}
  public void cancel(){status="CANCELLED";}
  public UUID getId(){return id;} public Account getAccount(){return account;} public String getDispatchConfirmationNumbers(){return dispatchConfirmationNumbers;} public String getWarnings(){return warnings;} public LocalDate getPickupDate(){return pickupDate;} public LocalTime getReadyTime(){return readyTime;} public LocalTime getCloseTime(){return closeTime;} public String getPickupAddress(){return pickupAddress;} public int getPackageCount(){return packageCount;} public BigDecimal getTotalWeight(){return totalWeight;} public String getStatus(){return status;} public Instant getCreatedAt(){return createdAt;}
}
