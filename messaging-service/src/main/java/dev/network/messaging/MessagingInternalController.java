package dev.network.messaging;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
public class MessagingInternalController {
  private final MessagingService service;

  public MessagingInternalController(MessagingService service) {
    this.service = service;
  }

  public record Request(@NotBlank String memberId, @NotBlank String conversationId) {}

  public record Eligibility(boolean allowed) {}

  @PostMapping("/internal/v1/messaging/notification-eligibility")
  public Eligibility eligibility(@Valid @RequestBody Request request) {
    java.util.UUID.fromString(request.memberId());
    java.util.UUID.fromString(request.conversationId());
    return new Eligibility(
        service.notificationAllowed(request.memberId(), request.conversationId()));
  }
}
