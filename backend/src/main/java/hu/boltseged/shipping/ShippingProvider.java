package hu.boltseged.shipping;
import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.*;
/** Carrier-neutral boundary. DHL DTOs never cross this boundary into JPA entities. */
public interface ShippingProvider {
  List<Quote> quote(QuoteRequest request); Result create(CreateRequest request); PickupResult createPickup(PickupRequest request); void cancelPickup(String dispatchConfirmationNumber,String requestorName,String reason);
  record Address(String companyName,String contactName,String countryCode,String postalCode,String cityName,String addressLine1,String addressLine2,String stateOrProvinceCode,String phone,String email){}
  record Package(BigDecimal weight,BigDecimal length,BigDecimal width,BigDecimal height){}
  record QuoteRequest(Address shipper,Address receiver,LocalDateTime plannedShippingDateAndTime,boolean customsDeclarable,List<Package> packages){}
  record Quote(String productCode,String productName,BigDecimal estimatedCost,String currency,String estimatedDelivery){}
  record ExportLineItem(int number,String description,BigDecimal price,int quantity,String quantityUnitOfMeasurement,String manufacturerCountry,BigDecimal netWeight,BigDecimal grossWeight,String exportReasonType,String commodityCode){}
  record ExportDeclaration(List<ExportLineItem> lineItems,String invoiceNumber,java.time.LocalDate invoiceDate){}
  record CustomsDocument(String typeCode,String imageFormat,String content){}
  record CreateRequest(String productCode,Address shipper,Address receiver,LocalDateTime plannedShippingDateAndTime,boolean customsDeclarable,String description,BigDecimal declaredValue,String declaredValueCurrency,String incoterm,ExportDeclaration exportDeclaration,List<Package> packages,List<CustomsDocument> customsDocuments){}
  record Label(String documentType,Integer packageIndex,String fileName,String contentType,byte[] content){}
  record Result(String masterTrackingNumber,String shipmentId,List<String> pieceTrackingNumbers,List<Label> labels){}
  record PickupShipment(String productCode,boolean customsDeclarable,String trackingNumber,BigDecimal declaredValue,String declaredValueCurrency,List<Package> packages){}
  record PickupRequest(LocalDateTime plannedPickupDateAndTime,Address shipper,Address pickupAddress,List<PickupShipment> shipments,java.time.LocalTime closeTime,String location,String locationType,String specialInstructions){}
  record PickupResult(List<String> dispatchConfirmationNumbers,String readyByTime,String nextPickupDate,List<String> warnings){}
}
