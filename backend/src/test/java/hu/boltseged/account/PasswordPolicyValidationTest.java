package hu.boltseged.account;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyValidationTest {
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void customerAndAdminPasswordRequestsUseSixCharacterMinimum() {
    assertTrue(validator.validate(new AccountPasswordController.Change("current", "abcdef")).isEmpty());
    assertFalse(validator.validate(new AccountPasswordController.Change("current", "abcde")).isEmpty());
    assertTrue(validator.validate(update(" ")).isEmpty());
    assertFalse(validator.validate(update("abcde")).isEmpty());
    assertTrue(validator.validate(update("abcdef")).isEmpty());
  }

  private AccountController.UpdateAccountRequest update(String password) {
    return new AccountController.UpdateAccountRequest(
        "Company", null, null, "Contact", "customer@example.com", password, "FIXED", BigDecimal.ZERO);
  }
}
