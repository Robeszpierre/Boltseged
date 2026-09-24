package hu.boltseged.account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountPasswordControllerTest {
  @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

  @Test void changesOnlyTheAuthenticatedAccountsPassword() {
    Account account = new Account(null, "customer@example.com", "old-hash", "CUSTOMER");
    AccountRepository repository = mock(AccountRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(encoder.matches("current-password", "old-hash")).thenReturn(true);
    when(encoder.encode("new-password-12")).thenReturn("new-hash");
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(account, null));

    new AccountPasswordController(repository, encoder).change(new AccountPasswordController.Change("current-password", "new-password-12"));

    assertEquals("new-hash", account.getPasswordHash());
    assertEquals("customer@example.com", account.getEmail());
    assertEquals("CUSTOMER", account.getRole());
    verify(repository).save(account);
  }

  @Test void rejectsAnIncorrectCurrentPassword() {
    Account account = new Account(null, "customer@example.com", "old-hash", "CUSTOMER");
    AccountRepository repository = mock(AccountRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(encoder.matches("wrong-password", "old-hash")).thenReturn(false);
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(account, null));

    ResponseStatusException error = assertThrows(ResponseStatusException.class, () ->
        new AccountPasswordController(repository, encoder).change(new AccountPasswordController.Change("wrong-password", "new-password-12")));

    assertEquals(400, error.getStatusCode().value());
    verify(repository, never()).save(any());
  }
}
