package hu.boltseged.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/accounts")
public class AccountController {
  private final AccountRepository repo;
  private final PasswordEncoder enc;

  AccountController(AccountRepository repo, PasswordEncoder enc) {
    this.repo = repo;
    this.enc = enc;
  }

  @GetMapping
  List<AccountView> list() {
    return repo.findAll().stream().map(AccountView::from).toList();
  }

  @PostMapping
  AccountView create(@Valid @RequestBody CreateAccountRequest request) {
    if (repo.findByEmailIgnoreCase(request.email()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
    }
    Account account = new Account(request.companyName(), request.email(), enc.encode(request.password()), "CUSTOMER");
    account.update(
        request.companyName(), request.taxNumber(), request.billingAddress(), request.contactName(), request.email(),
        request.pricingType() == null ? account.getPricingType() : request.pricingType(),
        request.pricingValue() == null ? account.getPricingValue() : request.pricingValue());
    return AccountView.from(repo.save(account));
  }

  @PutMapping("/{id}")
  AccountView update(@PathVariable UUID id, @Valid @RequestBody UpdateAccountRequest request) {
    Account account = get(id);
    account.update(
        request.companyName(), request.taxNumber(), request.billingAddress(), request.contactName(), request.email(),
        request.pricingType() == null ? account.getPricingType() : request.pricingType(),
        request.pricingValue() == null ? account.getPricingValue() : request.pricingValue());
    if (request.password() != null && !request.password().isBlank()) {
      account.setPasswordHash(enc.encode(request.password()));
    }
    return AccountView.from(repo.save(account));
  }

  @PatchMapping("/{id}/active")
  AccountView active(@PathVariable UUID id, @RequestParam boolean value) {
    Account account = get(id);
    account.setActive(value);
    return AccountView.from(repo.save(account));
  }

  @PostMapping("/{id}/password")
  void password(@PathVariable UUID id, @Valid @RequestBody PasswordRequest request) {
    Account account = get(id);
    account.setPasswordHash(enc.encode(request.password()));
    repo.save(account);
  }

  @GetMapping("/{id}")
  AccountView one(@PathVariable UUID id) {
    return AccountView.from(get(id));
  }

  private Account get(UUID id) {
    return repo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  record CreateAccountRequest(
      String companyName, String taxNumber, String billingAddress, String contactName,
      @Email @NotBlank String email, @NotBlank @Size(min = 6, message = "A jelszónak legalább 6 karakterből kell állnia.") String password,
      @Pattern(regexp = "PERCENTAGE|FIXED") String pricingType, @PositiveOrZero BigDecimal pricingValue) {}

  record UpdateAccountRequest(
      String companyName, String taxNumber, String billingAddress, String contactName,
      @Email @NotBlank String email, @Pattern(regexp = "^\\s*$|.{6,}", message = "A jelszónak legalább 6 karakterből kell állnia.") String password,
      @Pattern(regexp = "PERCENTAGE|FIXED") String pricingType, @PositiveOrZero BigDecimal pricingValue) {}

  record PasswordRequest(@NotBlank @Size(min = 6, message = "A jelszónak legalább 6 karakterből kell állnia.") String password) {}

  record AccountView(
      UUID id, String companyName, String taxNumber, String billingAddress, String contactName, String email,
      String pricingType, BigDecimal pricingValue, String role, boolean active) {
    static AccountView from(Account account) {
      return new AccountView(
          account.getId(), account.getCompanyName(), account.getTaxNumber(), account.getBillingAddress(),
          account.getContactName(), account.getEmail(), account.getPricingType(), account.getPricingValue(),
          account.getRole(), account.isActive());
    }
  }
}
