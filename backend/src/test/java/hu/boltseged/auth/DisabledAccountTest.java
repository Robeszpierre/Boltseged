package hu.boltseged.auth;

import hu.boltseged.account.Account;
import hu.boltseged.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DisabledAccountTest {
    @Test
    void disabledAccountCannotLogin() {
        AccountRepository repo = mock(AccountRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Account account = new Account("Disabled Kft.", "disabled@example.com", "hash", "CUSTOMER");
        account.setActive(false);
        when(repo.findByEmailIgnoreCase("disabled@example.com")).thenReturn(Optional.of(account));

        AuthController controller = new AuthController(repo, encoder, "local-development-secret-change-this-32-chars");
        ResponseEntity<?> response = controller.login(new AuthController.Login("disabled@example.com", "password"));

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(encoder);
    }
}
