package hu.boltseged.account;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountControllerTest {
  @Test
  void blankPasswordKeepsExistingPasswordHashDuringAdminUpdate() {
    AccountRepository repo = mock(AccountRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    Account account = new Account("Company", "customer@example.com", "old-hash", "CUSTOMER");
    when(repo.findById(account.getId())).thenReturn(Optional.of(account));
    when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    new AccountController(repo, encoder).update(account.getId(), updateRequest(""));

    assertEquals("old-hash", account.getPasswordHash());
    verify(encoder, never()).encode(any());
  }

  @Test
  void nonBlankPasswordIsEncodedDuringAdminUpdate() {
    AccountRepository repo = mock(AccountRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    Account account = new Account("Company", "customer@example.com", "old-hash", "CUSTOMER");
    when(repo.findById(account.getId())).thenReturn(Optional.of(account));
    when(repo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(encoder.encode("new-password-12")).thenReturn("new-hash");

    new AccountController(repo, encoder).update(account.getId(), updateRequest("new-password-12"));

    assertEquals("new-hash", account.getPasswordHash());
    verify(encoder).encode("new-password-12");
  }

  private AccountController.UpdateAccountRequest updateRequest(String password) {
    return new AccountController.UpdateAccountRequest(
        "Updated Company", null, null, "Contact", "customer@example.com", password,
        "FIXED", new BigDecimal("1000"));
  }
}
