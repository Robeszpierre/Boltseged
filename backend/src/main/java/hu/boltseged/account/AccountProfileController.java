package hu.boltseged.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
@RequestMapping("/api/account/profile")
public class AccountProfileController {
  private final AccountRepository repo;

  AccountProfileController(AccountRepository repo) {
    this.repo = repo;
  }

  @GetMapping
  Profile get() {
    return Profile.from(current());
  }

  @PutMapping
  Profile update(@Valid @RequestBody ProfileRequest request) {
    Account account = current();
    account.updateCompanyProfile(
        blankToNull(request.companyName()),
        blankToNull(request.taxNumber()),
        blankToNull(request.billingAddress()),
        blankToNull(request.contactName()),
        country(request.senderCountryCode()),
        blankToNull(request.senderPostalCode()),
        blankToNull(request.senderCityName()),
        blankToNull(request.senderAddressLine1()),
        blankToNull(request.senderAddressLine2()),
        blankToNull(request.senderStateOrProvinceCode()),
        blankToNull(request.senderPhone()),
        blankToNull(request.pickupCompanyName()),
        blankToNull(request.pickupContactName()),
        country(request.pickupCountryCode()),
        blankToNull(request.pickupPostalCode()),
        blankToNull(request.pickupCityName()),
        blankToNull(request.pickupAddressLine1()),
        blankToNull(request.pickupAddressLine2()),
        blankToNull(request.pickupStateOrProvinceCode()),
        blankToNull(request.pickupPhone()),
        blankToNull(request.pickupEmail()));
    return Profile.from(repo.save(account));
  }

  private Account current() {
    return (Account) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String country(String value) {
    String normalized = blankToNull(value);
    return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
  }

  public record ProfileRequest(
      String companyName,
      String taxNumber,
      String billingAddress,
      String contactName,
      @Pattern(regexp = "^$|[A-Za-z]{2}") String senderCountryCode,
      String senderPostalCode,
      String senderCityName,
      String senderAddressLine1,
      String senderAddressLine2,
      String senderStateOrProvinceCode,
      String senderPhone,
      String pickupCompanyName,
      String pickupContactName,
      @Pattern(regexp = "^$|[A-Za-z]{2}") String pickupCountryCode,
      String pickupPostalCode,
      String pickupCityName,
      String pickupAddressLine1,
      String pickupAddressLine2,
      String pickupStateOrProvinceCode,
      String pickupPhone,
      @Email String pickupEmail) {
    ProfileRequest(String companyName, String taxNumber, String billingAddress, String contactName) {
      this(companyName, taxNumber, billingAddress, contactName, null, null, null, null, null, null, null,
          null, null, null, null, null, null, null, null, null, null);
    }
  }

  public record Profile(
      String companyName,
      String taxNumber,
      String billingAddress,
      String contactName,
      String email,
      String senderCountryCode,
      String senderPostalCode,
      String senderCityName,
      String senderAddressLine1,
      String senderAddressLine2,
      String senderStateOrProvinceCode,
      String senderPhone,
      String pickupCompanyName,
      String pickupContactName,
      String pickupCountryCode,
      String pickupPostalCode,
      String pickupCityName,
      String pickupAddressLine1,
      String pickupAddressLine2,
      String pickupStateOrProvinceCode,
      String pickupPhone,
      String pickupEmail) {
    static Profile from(Account account) {
      return new Profile(
          account.getCompanyName(), account.getTaxNumber(), account.getBillingAddress(), account.getContactName(), account.getEmail(),
          account.getSenderCountryCode(), account.getSenderPostalCode(), account.getSenderCityName(), account.getSenderAddressLine1(),
          account.getSenderAddressLine2(), account.getSenderStateOrProvinceCode(), account.getSenderPhone(),
          account.getPickupCompanyName(), account.getPickupContactName(), account.getPickupCountryCode(), account.getPickupPostalCode(),
          account.getPickupCityName(), account.getPickupAddressLine1(), account.getPickupAddressLine2(),
          account.getPickupStateOrProvinceCode(), account.getPickupPhone(), account.getPickupEmail());
    }
  }
}
