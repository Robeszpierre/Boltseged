package hu.boltseged.auth;
import hu.boltseged.account.Account; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api") public class MeController { @GetMapping("/me") Object me(){Account a=(Account)SecurityContextHolder.getContext().getAuthentication().getPrincipal();return new Object(){public final Object id=a.getId();public final String companyName=a.getCompanyName();public final String email=a.getEmail();public final String role=a.getRole();};} }
