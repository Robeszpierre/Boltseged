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

import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class ShipmentService {
  private static final String INCOMPLETE_SENDER = "A küldemény létrehozásához előbb töltsd ki a Feladói adatok menüpontban a szükséges céges adatokat.";

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
    return dhl.quote(toQuote(sender, request.recipient(), request.plannedShippingDateAndTime(), request.customsDeclarable(), request.packages())).stream()
        .map(q -> new ShipmentController.CustomerQuote(q.productCode(), q.productName(), PricingCalculator.customerPrice(account, q.estimatedCost()), q.currency(), q.estimatedDelivery()))
        .toList();
  }

  public Shipment create(Account account, String key, ShipmentController.CreateRequest request) {
    Shipment old = shipments.findByAccountIdAndIdempotencyKey(account.getId(), key).orElse(null);
    if (old != null) return old;
    ShippingProvider.Address sender = sender(account);
    Shipment pending = createPending(account, key, request, sender);
    try {
      ShippingProvider.Quote selected = dhl.quote(toQuote(sender, request.recipient(), request.plannedShippingDateAndTime(), request.customsDeclarable(), request.packages())).stream()
          .filter(q -> q.productCode().equals(request.productCode()))
          .findFirst()
          .orElseThrow(() -> new DhlApiException(HttpStatus.BAD_REQUEST, List.of("The selected DHL product is no longer available")));
      return complete(pending.getId(), dhl.create(toCreate(sender, request, key)), selected.estimatedCost(), selected.currency());
    } catch (RuntimeException e) {
      markFailed(pending.getId());
      throw e;
    }
  }

  @Transactional
  public Shipment createPending(Account account, String key, ShipmentController.CreateRequest request, ShippingProvider.Address sender) {
    try {
      Shipment shipment = new Shipment(account, key, request.productCode(), request.shipDate(), safe(sender), safe(request.recipient()),
          request.packages().stream().map(p -> new Shipment.PackageInput(p.weight(), p.length(), p.width(), p.height())).toList());
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
  public void markFailed(UUID id) {
    shipments.findById(id).ifPresent(shipment -> {
      shipment.fail();
      shipments.save(shipment);
    });
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
    if (request.customsDeclarable()) {
      BigDecimal declaredValue = request.exportDeclaration().lineItems().stream()
          .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      snapshot.put("declaredValue", declaredValue);
      snapshot.put("declaredValueCurrency", request.declaredValueCurrency());
    }
    return snapshot;
  }

  private ShippingProvider.QuoteRequest toQuote(ShippingProvider.Address sender, ShipmentController.AddressRequest recipient,
      java.time.LocalDateTime planned, boolean customsDeclarable, List<ShipmentController.PackageRequest> packages) {
    return new ShippingProvider.QuoteRequest(sender, address(recipient), planned, customsDeclarable, packages(packages));
  }

  private ShippingProvider.CreateRequest toCreate(ShippingProvider.Address sender, ShipmentController.CreateRequest request, String key) {
    ShippingProvider.ExportDeclaration declaration = exportDeclaration(request.exportDeclaration(), key);
    BigDecimal declaredValue = request.customsDeclarable()
        ? request.exportDeclaration().lineItems().stream().map(i -> i.price().multiply(BigDecimal.valueOf(i.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add)
        : null;
    List<ShippingProvider.CustomsDocument> documents = request.customsDocuments() == null ? List.of()
        : request.customsDocuments().stream().map(d -> new ShippingProvider.CustomsDocument(d.typeCode(), d.imageFormat(), d.content())).toList();
    return new ShippingProvider.CreateRequest(request.productCode(), sender, address(request.recipient()), request.plannedShippingDateAndTime(),
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
