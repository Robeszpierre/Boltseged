package hu.boltseged.auth;

import hu.boltseged.account.Account;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
public class AuthSession {
  @Id
  private UUID id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant lastUsedAt;

  protected AuthSession() {}

  public AuthSession(Account account, String tokenHash, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.account = account;
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
    this.lastUsedAt = createdAt;
  }

  public Account getAccount() { return account; }
  public Instant getExpiresAt() { return expiresAt; }

  public void rotate(String tokenHash, Instant expiresAt) {
    this.tokenHash = tokenHash;
    this.expiresAt = expiresAt;
    this.lastUsedAt = Instant.now();
  }
}
