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
    private final dev.network.web.stream.DurableStream history;
    private final dev.network.web.stream.LiveStream live;

    public MessagingController(
            MessagingService service,
            dev.network.web.stream.DurableStream history,
            dev.network.web.stream.LiveStream live) {
        this.history = history;
        this.live = live;
        this.service = service;
    }

    @PostMapping
    public MessagingService.Conversation start(
            @AuthenticationPrincipal Jwt j, @Valid @RequestBody Start in) {
        return service.start(j.getSubject(), in.memberId());
    }

    @GetMapping
    public Pages.Slice<MessagingService.Conversation> list(
            @AuthenticationPrincipal Jwt j,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "false") boolean all) {
        return service.list(j.getSubject(), cursor, size, archived, all);
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

    @GetMapping("/sync")
    public java.util.Map<String, Object> sync(@AuthenticationPrincipal Jwt j) {
        String cursor = history.boundary(j.getSubject());
        return java.util.Map.of(
                "cursor",
                cursor,
                "unreadCount",
                service.unread(j.getSubject()),
                "state",
                service.list(j.getSubject(), null, 100, false, true));
    }

    @GetMapping(value = "/stream", produces = "text/event-stream")
    public void stream(
            @AuthenticationPrincipal Jwt j,
            @RequestHeader("Last-Event-ID") String cursor,
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        live.open(j.getSubject(), j.getExpiresAt(), cursor, request, response);
    }

    @PatchMapping("/{id}/preferences")
    public MessagingService.Conversation preferences(
            @AuthenticationPrincipal Jwt j, @PathVariable String id, @RequestBody Preferences input) {
        return service.preferences(j.getSubject(), id, input.muted(), input.archived());
    }

    public record Start(@NotBlank String memberId) {
    }

    public record Send(@NotBlank String clientMessageId, @NotBlank @Size(max = 4000) String body) {
    }

    public record Read(@NotBlank String messageId) {
    }

    public record Preferences(Boolean muted, Boolean archived) {
    }
}
