package hu.boltseged.account;

import hu.boltseged.config.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountProfileController.class)
@Import(SecurityConfig.class)
class AccountProfileHttpTest {
  @Autowired private MockMvc mvc;
  @MockBean private AccountRepository accounts;
  private Account customer;

  @BeforeEach
  void setUp() {
    customer = new Account(null, "customer@example.com", "hash", "CUSTOMER");
    when(accounts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void authenticatedCustomerCanSaveTheirProfile() throws Exception {
    mvc.perform(put("/api/account/profile")
            .with(SecurityMockMvcRequestPostProcessors.authentication(auth(customer)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"companyName\":\"Customer Kft\",\"contactName\":\"Customer\",\"senderCountryCode\":\"HU\",\"senderPostalCode\":\"1051\",\"senderCityName\":\"Budapest\",\"senderAddressLine1\":\"Street 1\",\"senderPhone\":\"+361\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyName").value("Customer Kft"))
        .andExpect(jsonPath("$.senderCountryCode").value("HU"));
  }

  @Test
  void anonymousProfileSaveIsUnauthorized() throws Exception {
    mvc.perform(put("/api/account/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidProfileInputIsValidationErrorNotAuthenticationFailure() throws Exception {
    mvc.perform(put("/api/account/profile")
            .with(SecurityMockMvcRequestPostProcessors.authentication(auth(customer)))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"senderCountryCode\":\"HUN\"}"))
        .andExpect(status().isBadRequest());
  }

  private UsernamePasswordAuthenticationToken auth(Account account) {
    return new UsernamePasswordAuthenticationToken(account, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
  }
}
