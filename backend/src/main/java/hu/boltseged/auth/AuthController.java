package hu.boltseged.auth;

import hu.boltseged.account.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  static final String REFRESH_COOKIE = "boltseged_refresh";

  private final AccountRepository accounts;
  private final PasswordEncoder encoder;
  private final AuthSessionRepository sessions;
  private final byte[] secret;
  private final long accessMinutes;
  private final long refreshDays;
  private final boolean secureCookie;
  private final SecureRandom random = new SecureRandom();

  @Autowired
  AuthController(AccountRepository accounts, PasswordEncoder encoder, AuthSessionRepository sessions,
      @Value("${app.jwt-secret}") String secret,
      @Value("${app.jwt-expiration-minutes}") long accessMinutes,
      @Value("${app.refresh-session-days}") long refreshDays,
      @Value("${app.refresh-cookie-secure:false}") boolean secureCookie) {
    this.accounts = accounts;
    this.encoder = encoder;
    this.sessions = sessions;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.accessMinutes = accessMinutes;
    this.refreshDays = refreshDays;
    this.secureCookie = secureCookie;
  }

  AuthController(AccountRepository accounts, PasswordEncoder encoder, String secret) {
    this(accounts, encoder, null, secret, 480, 30, false);
  }

  @PostMapping("/login")
  ResponseEntity<?> login(@Valid @RequestBody Login request, HttpServletResponse response) {
    return accounts.findByEmailIgnoreCase(request.email())
        .filter(account -> account.isActive() && encoder.matches(request.password(), account.getPasswordHash()))
        .<ResponseEntity<?>>map(account -> {
          if (sessions != null) createSession(account, response);
          return ResponseEntity.ok(new Token(accessToken(account), account.getRole()));
        })
        .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials")));
  }

  ResponseEntity<?> login(Login request) {
    return login(request, null);
  }

  @PostMapping("/refresh")
  ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletResponse response) {
    AuthSession session = refreshToken == null || sessions == null ? null : sessions.findByTokenHash(hash(refreshToken)).orElse(null);
    if (session == null || session.getExpiresAt().isBefore(Instant.now()) || !session.getAccount().isActive()) {
      if (session != null && sessions != null) sessions.delete(session);
      clearCookie(response);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "A munkamenet nem állítható helyre."));
    }
    String nextToken = newRefreshToken();
    session.rotate(hash(nextToken), expiry());
    sessions.save(session);
    writeCookie(response, nextToken);
    Account account = session.getAccount();
    return ResponseEntity.ok(new Token(accessToken(account), account.getRole()));
  }

  @PostMapping("/logout")
  ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken, HttpServletResponse response) {
    if (refreshToken != null && sessions != null) sessions.findByTokenHash(hash(refreshToken)).ifPresent(sessions::delete);
    clearCookie(response);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/register")
  ResponseEntity<?> register(@Valid @RequestBody Register request) {
    if (accounts.findByEmailIgnoreCase(request.email()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
    }
    Account account = accounts.save(new Account(null, request.email(), encoder.encode(request.password()), "CUSTOMER"));
    return ResponseEntity.status(HttpStatus.CREATED).body(new Registered(account.getId(), account.getEmail(), account.getRole()));
  }

  private String accessToken(Account account) {
    return Jwts.builder()
        .subject(account.getEmail())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + accessMinutes * 60_000))
        .signWith(Keys.hmacShaKeyFor(secret))
        .compact();
  }

  private void createSession(Account account, HttpServletResponse response) {
    String token = newRefreshToken();
    sessions.save(new AuthSession(account, hash(token), expiry()));
    writeCookie(response, token);
  }

  private Instant expiry() {
    return Instant.now().plusSeconds(refreshDays * 86_400);
  }

  private String newRefreshToken() {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private void writeCookie(HttpServletResponse response, String token) {
    if (response == null) return;
    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, token)
        .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/api/auth")
        .maxAge(refreshDays * 86_400).build().toString());
  }

  private void clearCookie(HttpServletResponse response) {
    if (response == null) return;
    response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
        .httpOnly(true).secure(secureCookie).sameSite("Lax").path("/api/auth").maxAge(0).build().toString());
  }

  record Login(@Email @NotBlank String email, @NotBlank String password) {}
  record Register(@Email @NotBlank String email, @NotBlank @Size(min = 6, message = "A jelszónak legalább 6 karakterből kell állnia.") String password) {}
  record Token(String token, String role) {}
  record Registered(UUID id, String email, String role) {}
}
