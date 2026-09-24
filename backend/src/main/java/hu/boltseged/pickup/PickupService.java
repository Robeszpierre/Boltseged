package hu.boltseged.pickup;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import hu.boltseged.account.Account;
import hu.boltseged.shipment.*;
import hu.boltseged.shipping.ShippingProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;

@Service public class PickupService {
  private final PickupBookingRepository bookings; private final ShipmentRepository shipments; private final ShippingProvider dhl; private final ObjectMapper json;
  public PickupService(PickupBookingRepository b,ShipmentRepository s,ShippingProvider d,ObjectMapper j){bookings=b;shipments=s;dhl=d;json=j;}
  @Transactional public PickupBooking create(Account account,PickupController.CreateRequest request){
    ShippingProvider.Address pickup=pickupAddress(account);
    List<UUID> unique=request.shipmentIds().stream().distinct().toList();
    List<Shipment> selected=shipments.findAllForPickupByIdIn(unique);
    if(selected.size()!=unique.size()||selected.stream().anyMatch(s->!s.getAccount().getId().equals(account.getId())||!"CREATED".equals(s.getStatus())))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Egy vagy több küldemény nem található.");
    Shipment booked=selected.stream().filter(s->bookings.existsByStatusAndShipments_Id("BOOKED",s.getId())).findFirst().orElse(null);
    if(booked!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Ehhez a küldeményhez már tartozik aktív futárrendelés: "+booked.getMasterTrackingNumber());
    int packageCount=selected.stream().mapToInt(s->s.getPackages().size()).sum();
    BigDecimal totalWeight=selected.stream().flatMap(s->s.getPackages().stream()).map(ShipmentPackage::getWeight).reduce(BigDecimal.ZERO,BigDecimal::add);
    ShippingProvider.Address shipper=address(readAddress(selected.getFirst().getSender()));
    List<ShippingProvider.PickupShipment> details=selected.stream().map(this::pickupShipment).toList();
    ShippingProvider.PickupResult result=dhl.createPickup(new ShippingProvider.PickupRequest(request.pickupDate().atTime(request.readyTime()),shipper,pickup,details,request.closeTime(),request.location(),request.locationType(),request.specialInstructions()));
    PickupBooking booking=new PickupBooking(account,write(result.dispatchConfirmationNumbers()),write(result.warnings()),request.pickupDate(),request.readyTime(),request.closeTime(),write(pickup),packageCount,totalWeight,selected);
    return bookings.save(booking);
  }
  public List<PickupBooking> list(Account account){return bookings.findByAccountIdOrderByCreatedAtDesc(account.getId());}
  @Transactional public PickupBooking cancel(Account account,UUID id){PickupBooking booking=bookings.findByIdAndAccountId(id,account.getId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if("CANCELLED".equals(booking.getStatus()))return booking;List<String> confirmations=readStrings(booking.getDispatchConfirmationNumbers());if(confirmations.isEmpty())throw new IllegalStateException("Pickup confirmation is missing");String requestor=account.getContactName()==null||account.getContactName().isBlank()?account.getEmail():account.getContactName();for(String confirmation:confirmations)dhl.cancelPickup(confirmation,requestor,"Customer cancelled pickup");booking.cancel();return bookings.save(booking);}
  private ShippingProvider.PickupShipment pickupShipment(Shipment shipment){CustomsSnapshot customs=customs(shipment);if(customs.declarable()&&(customs.declaredValue()==null||customs.declaredValue().signum()<=0||customs.declaredValueCurrency()==null||!customs.declaredValueCurrency().matches("[A-Z]{3}")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A kiválasztott vámköteles küldeményhez nem áll rendelkezésre a szükséges vámérték vagy pénznem: "+shipment.getMasterTrackingNumber());return new ShippingProvider.PickupShipment(shipment.getDhlProductCode(),customs.declarable(),shipment.getMasterTrackingNumber(),customs.declaredValue(),customs.declaredValueCurrency(),shipment.getPackages().stream().map(p->new ShippingProvider.Package(p.getWeight(),p.getLength(),p.getWidth(),p.getHeight())).toList());}
  private CustomsSnapshot customs(Shipment shipment){try{JsonNode node=json.readTree(shipment.getCustoms());if(node.has("customsDeclarable"))return new CustomsSnapshot(node.path("customsDeclarable").asBoolean(),node.path("declaredValue").isNumber()?node.path("declaredValue").decimalValue():null,node.path("declaredValueCurrency").asText(null));}catch(Exception ignored){}Map<String,Object> from=readAddress(shipment.getSender()),to=readAddress(shipment.getRecipient());String a=String.valueOf(from.getOrDefault("countryCode","")),b=String.valueOf(to.getOrDefault("countryCode",""));Set<String> eu=Set.of("AT","BE","BG","HR","CY","CZ","DK","EE","FI","FR","DE","GR","HU","IE","IT","LV","LT","LU","MT","NL","PL","PT","RO","SK","SI","ES","SE");return new CustomsSnapshot(!a.equals(b)&&!(eu.contains(a)&&eu.contains(b)),null,null);}
  private record CustomsSnapshot(boolean declarable,BigDecimal declaredValue,String declaredValueCurrency){}
  private Map<String,Object> readAddress(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Invalid shipment address snapshot",e);}}
  private List<String> readStrings(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException(e);}}
  private ShippingProvider.Address pickupAddress(Account account){if(blank(account.getPickupCompanyName())||blank(account.getPickupContactName())||account.getPickupCountryCode()==null||!account.getPickupCountryCode().matches("[A-Z]{2}")||blank(account.getPickupPostalCode())||blank(account.getPickupCityName())||blank(account.getPickupAddressLine1())||blank(account.getPickupPhone()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A futár rendeléséhez előbb töltsd ki a Feladói adatok menüpontban a felvételi címet.");return new ShippingProvider.Address(account.getPickupCompanyName(),account.getPickupContactName(),account.getPickupCountryCode(),account.getPickupPostalCode(),account.getPickupCityName(),account.getPickupAddressLine1(),account.getPickupAddressLine2(),account.getPickupStateOrProvinceCode(),account.getPickupPhone(),account.getPickupEmail());}
  private boolean blank(String value){return value==null||value.isBlank();}
  private ShippingProvider.Address address(Map<String,Object> a){return new ShippingProvider.Address(text(a,"companyName"),text(a,"contactName"),text(a,"countryCode"),text(a,"postalCode"),text(a,"cityName"),text(a,"addressLine1"),text(a,"addressLine2"),text(a,"stateOrProvinceCode"),text(a,"phone"),text(a,"email"));}
  private String text(Map<String,Object> a,String key){Object value=a.get(key);return value==null?null:String.valueOf(value);}
  private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Invalid pickup data",e);}}
}
