package dev.network.notification.preferences;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications/preferences/job-alerts")
public class JobAlertPreferenceController {
  private final JobAlertPreferences service;

  public JobAlertPreferenceController(JobAlertPreferences service) {
    this.service = service;
  }

  public record Input(@NotNull Boolean enabled) {}

  @GetMapping
  public JobAlertPreferences.View get(@AuthenticationPrincipal Jwt j) {
    return service.get(j.getSubject());
  }

  @PutMapping
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void set(@AuthenticationPrincipal Jwt j, @Valid @RequestBody Input in) {
    service.set(j.getSubject(), in.enabled());
  }
}
