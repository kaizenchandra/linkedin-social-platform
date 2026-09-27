package dev.network.hiring.company;

import dev.network.hiring.shared.HiringPages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CompanyController {
  private final CompanyService service;

  public CompanyController(CompanyService service) {
    this.service = service;
  }

  public record MemberInput(@NotBlank String memberId) {}

  @PostMapping("/companies")
  public CompanyService.View create(
      @AuthenticationPrincipal Jwt j, @Valid @RequestBody CompanyService.Input in) {
    return service.create(j.getSubject(), in);
  }

  @PutMapping("/companies/{id}")
  public CompanyService.View edit(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @Valid @RequestBody CompanyService.Input in) {
    return service.edit(j.getSubject(), id, in);
  }

  @GetMapping("/companies/{id}")
  public CompanyService.View get(@PathVariable String id) {
    return service.get(id);
  }

  @GetMapping("/companies")
  public HiringPages.Slice<CompanyService.View> list(
      @AuthenticationPrincipal Jwt j,
      @RequestParam(defaultValue = "false") boolean mine,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(j.getSubject(), mine, cursor, size);
  }

  @GetMapping("/companies/{id}/members")
  public List<CompanyService.Membership> members(
      @AuthenticationPrincipal Jwt j, @PathVariable String id) {
    return service.members(j.getSubject(), id);
  }

  @DeleteMapping("/companies/{id}/members/{memberId}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void remove(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @PathVariable String memberId) {
    service.remove(j.getSubject(), id, memberId);
  }

  @PostMapping("/companies/{id}/ownership")
  public CompanyService.View transfer(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @Valid @RequestBody MemberInput in) {
    return service.transfer(j.getSubject(), id, in.memberId());
  }

  @PostMapping("/companies/{id}/invitations")
  public CompanyService.Invitation invite(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @Valid @RequestBody MemberInput in) {
    return service.invite(j.getSubject(), id, in.memberId());
  }

  @GetMapping("/company-invitations")
  public HiringPages.Slice<CompanyService.Invitation> invitations(
      @AuthenticationPrincipal Jwt j,
      @RequestParam(required = false) String companyId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.invitations(j.getSubject(), companyId, cursor, size);
  }

  @PostMapping("/company-invitations/{id}/{action}")
  public CompanyService.Invitation action(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @PathVariable String action) {
    return service.invitationAction(j.getSubject(), id, action);
  }

  @GetMapping("/companies/{id}/audit")
  public List<CompanyService.Audit> audit(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.auditHistory(j.getSubject(), id, page, size);
  }
}
