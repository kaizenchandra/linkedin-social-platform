package dev.network.notification.inbox;

import dev.network.web.Pages;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationRepository repo;
  private final Clock clock;

  public NotificationController(NotificationRepository r, Clock c) {
    repo = r;
    clock = c;
  }

  public record View(
      String id,
      String eventType,
      String actorId,
      String resourceId,
      Instant occurredAt,
      Instant readAt,
      @com.fasterxml.jackson.annotation.JsonInclude(
              com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
          String message) {}

  @GetMapping
  @Transactional(readOnly = true)
  public List<View> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return repo
        .findByRecipientIdOrderByOccurredAtDescIdDesc(
            jwt.getSubject(), PageRequest.of(Pages.page(page), Pages.size(size)))
        .stream()
        .map(this::view)
        .toList();
  }

  @PutMapping("/{id}/read")
  @Transactional
  public View read(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) {
    var n =
        repo.owned(id, jwt.getSubject())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    if (n.readAt == null)
      n.readAt = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    return view(n);
  }

  private View view(Notification n) {
    return new View(
        n.id,
        n.eventType,
        n.actorId,
        n.resourceId,
        n.occurredAt,
        n.readAt,
        n.eventType.equals("hiring.job.alert") ? "A new job matches your saved search." : null);
  }
}
