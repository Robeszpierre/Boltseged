package hu.boltseged.shipment;

import com.fasterxml.jackson.databind.ObjectMapper;
import hu.boltseged.account.Account;
import hu.boltseged.billing.PricingCalculator;
import hu.boltseged.label.*;
import hu.boltseged.shipping.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class ShipmentService {
  private static final String INCOMPLETE_SENDER = "A küldemény létrehozásához előbb töltsd ki a Feladói adatok menüpontban a szükséges céges adatokat.";
  private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

  private final ShipmentRepository shipments;
  private final ShipmentLabelRepository labels;
  private final ShippingProvider dhl;
  private final LabelStorage storage;
  private final ObjectMapper json;

  public ShipmentService(ShipmentRepository shipments, ShipmentLabelRepository labels, ShippingProvider dhl, LabelStorage storage, ObjectMapper json) {
    this.shipments = shipments;
    this.labels = labels;
    this.dhl = dhl;
    this.storage = storage;
    this.json = json;
  }

  public List<ShipmentController.CustomerQuote> quote(Account account, ShipmentController.QuoteRequest request) {
    ShippingProvider.Address sender = sender(account);
    validateIncoterm(request.customsDeclarable(), request.incoterm());
    return dhl.quote(toQuote(sender, address(request.recipient()), request.plannedShippingDateAndTime(), request.customsDeclarable(), request.incoterm(), request.packages())).stream()
        .map(q -> new ShipmentController.CustomerQuote(q.productCode(), q.productName(), PricingCalculator.customerPrice(account, q.estimatedCost()), q.currency(), q.estimatedDelivery()))
        .toList();
  }

  public Shipment create(Account account, String key, ShipmentController.CreateRequest request) {
    Shipment old = shipments.findByAccountIdAndIdempotencyKey(account.getId(), key).orElse(null);
    if (old != null) return old;
    validateRecipient(request.recipient());
    validateDescription(request);
    validateIncoterm(request.customsDeclarable(), request.incoterm());
    validateCustomsWeights(request);
    ShippingProvider.Address sender = sender(account);
    ShippingProvider.Address recipient = address(request.recipient());
    ShippingProvider.Quote selected = dhl.quote(toQuote(sender, recipient, request.plannedShippingDateAndTime(), request.customsDeclarable(), request.incoterm(), request.packages())).stream()
        .filter(q -> q.productCode().equals(request.productCode()))
        .findFirst()
        .orElseThrow(() -> new DhlApiException(HttpStatus.BAD_REQUEST, List.of("The selected DHL product is no longer available")));
    Shipment pending = createPending(account, key, request, sender, recipient, selected.currency());
    try {
      return complete(pending.getId(), dhl.create(toCreate(sender, recipient, request, key)), selected.estimatedCost(), selected.currency());
    } catch (DhlApiException e) {
      log.warn("DHL shipment creation failed for shipment {} with HTTP {}: {}", pending.getId(), e.status().value(), String.join(" | ", e.messages()));
      markFailed(pending.getId(), dhlFailureReason(e));
      throw e;
    } catch (RuntimeException e) {
      log.warn("Shipment creation failed for shipment {}: {}", pending.getId(), e.getMessage());
      markFailed(pending.getId(), "A küldemény létrehozása sikertelen volt. Próbáld meg később újra.");
      throw e;
    }
  }

  @Transactional
  public Shipment createPending(Account account, String key, ShipmentController.CreateRequest request, ShippingProvider.Address sender, ShippingProvider.Address recipient, String currency) {
    try {
      Shipment shipment = new Shipment(account, key, request.productCode(), request.shipDate(), safe(sender), safe(recipient),
          request.packages().stream().map(p -> new Shipment.PackageInput(p.weight(), p.length(), p.width(), p.height())).toList());
      shipment.setQuoteCurrency(currency);
      shipment.setCustoms(safe(customsSnapshot(request)));
      return shipments.saveAndFlush(shipment);
    } catch (DataIntegrityViolationException e) {
      return shipments.findByAccountIdAndIdempotencyKey(account.getId(), key).orElseThrow();
    }
  }

  @Transactional
  public Shipment complete(UUID id, ShippingProvider.Result result, BigDecimal cost, String currency) {
    Shipment shipment = shipments.findById(id).orElseThrow();
    shipment.price(cost, PricingCalculator.customerPrice(shipment.getAccount(), cost));
    shipment.complete(result.masterTrackingNumber(), result.shipmentId(), result.pieceTrackingNumbers(), cost, currency);
    for (ShippingProvider.Label label : result.labels()) {
      try {
        ShipmentPackage shipmentPackage = label.packageIndex() == null ? null : shipment.getPackages().get(label.packageIndex() - 1);
        String key = storage.put(shipment.getId(), label.fileName(), label.contentType(), new ByteArrayInputStream(label.content()));
        labels.save(new ShipmentLabel(shipment, shipmentPackage, key, label.fileName(), label.contentType(), label.documentType()));
      } catch (IOException e) {
        throw new IllegalStateException("DHL shipment was created but a label could not be stored", e);
      }
    }
    return shipments.save(shipment);
  }

  @Transactional
  public void markFailed(UUID id, String reason) {
    shipments.findById(id).ifPresent(shipment -> {
      shipment.fail(reason);
      shipments.save(shipment);
    });
  }

  private void validateRecipient(ShipmentController.AddressRequest recipient) {
    if ("US".equals(recipient.countryCode()) && (recipient.stateOrProvinceCode() == null || !recipient.stateOrProvinceCode().matches("[A-Za-z]{2}"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amerikai címzettnél add meg a kétbetűs államkódot.");
    }
  }

  private void validateCustomsWeights(ShipmentController.CreateRequest request) {
    if (!request.customsDeclarable()) return;
    if (request.exportDeclaration() == null || request.exportDeclaration().lineItems() == null || request.packages() == null) return;
    for (ShipmentController.ExportLineItemRequest item : request.exportDeclaration().lineItems()) {
      if (item.netWeight() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add meg a tétel nettó tömegét.");
      if (item.grossWeight() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Add meg a tétel bruttó tömegét.");
      if (item.netWeight().signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nettó tömegnek 0-nál nagyobbnak kell lennie.");
      if (item.grossWeight().signum() <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A bruttó tömegnek 0-nál nagyobbnak kell lennie.");
      if (item.grossWeight().compareTo(item.netWeight()) < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A bruttó tömeg nem lehet kisebb a nettó tömegnél.");
    }
    BigDecimal customsGross = request.exportDeclaration().lineItems().stream().map(ShipmentController.ExportLineItemRequest::grossWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal packageWeight = request.packages().stream().map(ShipmentController.PackageRequest::weight).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    if (customsGross.compareTo(packageWeight) > 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A vámáruk összes bruttó tömege nem lehet nagyobb a csomagok teljes súlyánál.");
    }
  }

  private void validateDescription(ShipmentController.CreateRequest request) {
    if (request.customsDeclarable() && request.description() != null && request.description().length() > 70) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A küldemény tartalmának leírása legfeljebb 70 karakter lehet.");
    }
  }

  private void validateIncoterm(boolean customsDeclarable, String incoterm) {
    if (customsDeclarable && blank(incoterm)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Válassz Incotermet.");
    }
  }

  private String dhlFailureReason(DhlApiException error) {
    String details = String.join(" ", error.messages()).replaceAll("[\\r\\n]+", " ").trim();
    if (details.isBlank()) return "A DHL nem tudta feldolgozni a küldeményt. Ellenőrizd az adatokat és próbáld újra.";
    return details.length() <= 2000 ? details : details.substring(0, 2000);
  }

  private ShippingProvider.Address sender(Account account) {
    if (blank(account.getCompanyName()) || blank(account.getContactName()) || blank(account.getEmail())
        || !country(account.getSenderCountryCode()) || blank(account.getSenderPostalCode()) || blank(account.getSenderCityName())
        || blank(account.getSenderAddressLine1()) || blank(account.getSenderPhone())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INCOMPLETE_SENDER);
    }
    return new ShippingProvider.Address(account.getCompanyName(), account.getContactName(), account.getSenderCountryCode(),
        account.getSenderPostalCode(), account.getSenderCityName(), account.getSenderAddressLine1(), account.getSenderAddressLine2(),
        account.getSenderStateOrProvinceCode(), account.getSenderPhone(), account.getEmail());
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private boolean country(String value) {
    return value != null && value.matches("[A-Z]{2}");
  }

  private Map<String, Object> customsSnapshot(ShipmentController.CreateRequest request) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("customsDeclarable", request.customsDeclarable());
    snapshot.put("description", request.description());
    if (request.customsDeclarable()) {
      BigDecimal declaredValue = request.exportDeclaration().lineItems().stream()
          .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      snapshot.put("declaredValue", declaredValue);
      snapshot.put("declaredValueCurrency", request.declaredValueCurrency());
      snapshot.put("incoterm", request.incoterm());
      snapshot.put("lineItems", request.exportDeclaration().lineItems().stream().map(this::copyLineItem).toList());
    }
    return snapshot;
  }

  private Map<String, Object> copyLineItem(ShipmentController.ExportLineItemRequest item) {
    Map<String, Object> line = new LinkedHashMap<>();
    line.put("number", item.number());
    line.put("description", item.description());
    line.put("price", item.price());
    line.put("quantity", item.quantity());
    line.put("quantityUnitOfMeasurement", item.quantityUnitOfMeasurement());
    line.put("manufacturerCountry", item.manufacturerCountry());
    line.put("netWeight", item.netWeight());
    line.put("grossWeight", item.grossWeight());
    line.put("exportReasonType", item.exportReasonType());
    line.put("commodityCode", item.commodityCode());
    return line;
  }

  private ShippingProvider.QuoteRequest toQuote(ShippingProvider.Address sender, ShippingProvider.Address recipient,
      java.time.LocalDateTime planned, boolean customsDeclarable, String incoterm, List<ShipmentController.PackageRequest> packages) {
    return new ShippingProvider.QuoteRequest(sender, recipient, planned, customsDeclarable, incoterm, packages(packages));
  }

  private ShippingProvider.CreateRequest toCreate(ShippingProvider.Address sender, ShippingProvider.Address recipient, ShipmentController.CreateRequest request, String key) {
    ShippingProvider.ExportDeclaration declaration = exportDeclaration(request.exportDeclaration(), key);
    BigDecimal declaredValue = request.customsDeclarable()
        ? request.exportDeclaration().lineItems().stream().map(i -> i.price().multiply(BigDecimal.valueOf(i.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add)
        : null;
    List<ShippingProvider.CustomsDocument> documents = request.customsDocuments() == null ? List.of()
        : request.customsDocuments().stream().map(d -> new ShippingProvider.CustomsDocument(d.typeCode(), d.imageFormat(), d.content())).toList();
    return new ShippingProvider.CreateRequest(request.productCode(), sender, recipient, request.plannedShippingDateAndTime(),
        request.customsDeclarable(), request.description(), declaredValue, request.declaredValueCurrency(), request.incoterm(), declaration,
        packages(request.packages()), documents);
  }

  private ShippingProvider.ExportDeclaration exportDeclaration(ShipmentController.ExportDeclarationRequest request, String key) {
    if (request == null) return null;
    return new ShippingProvider.ExportDeclaration(request.lineItems().stream()
        .map(i -> new ShippingProvider.ExportLineItem(i.number(), i.description(), i.price(), i.quantity(), i.quantityUnitOfMeasurement(),
            i.manufacturerCountry(), i.netWeight(), i.grossWeight(), i.exportReasonType(), i.commodityCode()))
        .toList(), invoiceNumber(key), request.invoiceDate());
  }

  static String invoiceNumber(String idempotencyKey) {
    String compact = idempotencyKey.replace("-", "");
    if (compact.matches("(?i)[0-9a-f]{32}")) return "BS-" + compact.toLowerCase(Locale.ROOT);
    try {
      return "BS-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(idempotencyKey.getBytes(StandardCharsets.UTF_8)), 0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private List<ShippingProvider.Package> packages(List<ShipmentController.PackageRequest> packages) {
    return packages.stream().map(p -> new ShippingProvider.Package(p.weight(), p.length(), p.width(), p.height())).toList();
  }

  private ShippingProvider.Address address(ShipmentController.AddressRequest address) {
    return new ShippingProvider.Address(address.companyName(), address.contactName(), address.countryCode(), address.postalCode(),
        address.cityName(), address.addressLine1(), address.addressLine2(), address.stateOrProvinceCode(), address.phone(), address.email());
  }

  private String safe(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid address", e);
    }
  }
}
