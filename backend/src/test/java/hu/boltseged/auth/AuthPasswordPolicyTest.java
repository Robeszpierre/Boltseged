package hu.boltseged.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthPasswordPolicyTest {
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void registrationAcceptsSixCharactersAndRejectsShorterPasswords() {
    assertTrue(validator.validate(new AuthController.Register("customer@example.com", "abcdef")).isEmpty());
    assertEquals("A jelszónak legalább 6 karakterből kell állnia.",
        validator.validate(new AuthController.Register("customer@example.com", "abcde")).iterator().next().getMessage());
  }
}
