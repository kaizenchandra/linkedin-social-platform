package dev.network.content.moderation;

import dev.network.web.Pages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ModerationController {
  private final ModerationService service;

  public ModerationController(ModerationService s) {
    service = s;
  }

  public record Input(
      @NotNull ModerationService.TargetType targetType,
      @NotBlank String targetId,
      @NotNull ModerationService.Reason reason,
      @Size(max = 1000) String explanation) {}

  public record Inspection(@NotBlank @Size(max = 1000) String reason) {}

  public record Command(
      @NotNull ModerationService.Action action, @NotBlank @Size(max = 1000) String reason) {}

  @PostMapping("/api/v1/reports")
  public ModerationService.Report submit(
      @AuthenticationPrincipal Jwt j, @Valid @RequestBody Input in) {
    return service.submit(
        j.getSubject(), in.targetType(), in.targetId(), in.reason(), in.explanation());
  }

  @GetMapping("/api/v1/reports")
  public List<ModerationService.Report> own(
      @AuthenticationPrincipal Jwt j,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.own(j.getSubject(), page, size);
  }

  @GetMapping("/api/v1/moderation/reports")
  public Pages.Slice<ModerationService.Report> queue(
      @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size) {
    return service.queue(cursor, size);
  }

  @PostMapping("/api/v1/moderation/reports/{id}/inspect")
  public ModerationService.Inspection inspect(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @Valid @RequestBody Inspection in) {
    return service.inspect(j.getSubject(), id, in.reason());
  }

  @PostMapping("/api/v1/moderation/reports/{id}/actions")
  public ModerationService.Report act(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @Valid @RequestBody Command in) {
    return service.act(j.getSubject(), id, in.action(), in.reason());
  }

  @GetMapping("/api/v1/moderation/reports/{id}/audit")
  public List<ModerationService.Audit> history(
      @PathVariable String id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.history(id, page, size);
  }
}
