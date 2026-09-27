package dev.network.messaging;

import dev.network.web.Pages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conversations")
public class MessagingController {
  private final MessagingService service;

  public MessagingController(MessagingService service) {
    this.service = service;
  }

  public record Start(@NotBlank String memberId) {}

  public record Send(@NotBlank String clientMessageId, @NotBlank @Size(max = 4000) String body) {}

  public record Read(@NotBlank String messageId) {}

  @PostMapping
  public MessagingService.Conversation start(
      @AuthenticationPrincipal Jwt j, @Valid @RequestBody Start in) {
    return service.start(j.getSubject(), in.memberId());
  }

  @GetMapping
  public Pages.Slice<MessagingService.Conversation> list(
      @AuthenticationPrincipal Jwt j,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(j.getSubject(), cursor, size);
  }

  @GetMapping("/{id}")
  public MessagingService.Conversation get(
      @AuthenticationPrincipal Jwt j, @PathVariable String id) {
    return service.get(j.getSubject(), id);
  }

  @PostMapping("/{id}/messages")
  public MessagingService.Message send(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @Valid @RequestBody Send in) {
    return service.send(j.getSubject(), id, in.clientMessageId(), in.body());
  }

  @GetMapping("/{id}/messages")
  public Pages.Slice<MessagingService.Message> history(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.history(j.getSubject(), id, cursor, size);
  }

  @PutMapping("/{id}/read")
  public MessagingService.Conversation read(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @Valid @RequestBody Read in) {
    return service.read(j.getSubject(), id, in.messageId());
  }
}
