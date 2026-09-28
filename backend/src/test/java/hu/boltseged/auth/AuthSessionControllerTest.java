package hu.boltseged.auth;

import hu.boltseged.account.Account;
import hu.boltseged.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthSessionControllerTest {
  private static final String SECRET = "01234567890123456789012345678901";

  @Test
  void loginCreatesHttpOnlyRefreshSessionAndRefreshRotatesIt() throws Exception {
    AccountRepository accounts = mock(AccountRepository.class);
    AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    Account account = new Account("Customer", "customer@example.com", "hash", "CUSTOMER");
    when(accounts.findByEmailIgnoreCase(account.getEmail())).thenReturn(Optional.of(account));
    when(encoder.matches("secret", "hash")).thenReturn(true);
    when(sessions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    AuthController controller = new AuthController(accounts, encoder, sessions, SECRET, 15, 30, false);

    MockHttpServletResponse loginResponse = new MockHttpServletResponse();
    assertEquals(200, controller.login(new AuthController.Login(account.getEmail(), "secret"), loginResponse).getStatusCode().value());
    String refreshToken = cookieValue(loginResponse.getHeader("Set-Cookie"));
    assertNotNull(refreshToken);
    assertTrue(loginResponse.getHeader("Set-Cookie").contains("HttpOnly"));
    assertTrue(loginResponse.getHeader("Set-Cookie").contains("SameSite=Lax"));

    AuthSession session = new AuthSession(account, hash(refreshToken), Instant.now().plusSeconds(60));
    when(sessions.findByTokenHash(hash(refreshToken))).thenReturn(Optional.of(session));
    MockHttpServletResponse refreshResponse = new MockHttpServletResponse();
    var refreshed = controller.refresh(refreshToken, refreshResponse);

    assertEquals(200, refreshed.getStatusCode().value());
    assertNotEquals(refreshToken, cookieValue(refreshResponse.getHeader("Set-Cookie")));
    verify(sessions, atLeastOnce()).save(session);
  }

  @Test
  void logoutRevokesSessionAndPreventsLaterRefresh() throws Exception {
    AccountRepository accounts = mock(AccountRepository.class);
    AuthSessionRepository sessions = mock(AuthSessionRepository.class);
    Account account = new Account("Customer", "customer@example.com", "hash", "CUSTOMER");
    String refreshToken = "refresh-token";
    AuthSession session = new AuthSession(account, hash(refreshToken), Instant.now().plusSeconds(60));
    when(sessions.findByTokenHash(hash(refreshToken))).thenReturn(Optional.of(session), Optional.empty());
    AuthController controller = new AuthController(accounts, mock(PasswordEncoder.class), sessions, SECRET, 15, 30, false);

    MockHttpServletResponse logoutResponse = new MockHttpServletResponse();
    assertEquals(204, controller.logout(refreshToken, logoutResponse).getStatusCode().value());
    verify(sessions).delete(session);
    assertTrue(logoutResponse.getHeader("Set-Cookie").contains("Max-Age=0"));

    assertEquals(401, controller.refresh(refreshToken, new MockHttpServletResponse()).getStatusCode().value());
  }

  private static String cookieValue(String header) {
    return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
  }

  private static String hash(String token) throws Exception {
    return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
  }
}
