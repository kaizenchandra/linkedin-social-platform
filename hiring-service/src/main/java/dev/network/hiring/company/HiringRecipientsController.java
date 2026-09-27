package dev.network.hiring.company;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
@RestController
public class HiringRecipientsController {
 private final CompanyService companies;
 private final JdbcTemplate db;
 public HiringRecipientsController(CompanyService companies, JdbcTemplate db) {this.companies=companies;this.db=db;}
 public record Input(@NotBlank String companyId) {}
 public record Recipients(List<String> memberIds) {}
 @PostMapping("/internal/v1/hiring/recipients") @Transactional
 public Recipients recipients(@Valid @RequestBody Input input) {
  companies.lock(input.companyId());
  return new Recipients(db.queryForList("SELECT member_id FROM company_members WHERE company_id=? ORDER BY member_id FETCH FIRST 101 ROWS ONLY",String.class,input.companyId()));
 }
}
