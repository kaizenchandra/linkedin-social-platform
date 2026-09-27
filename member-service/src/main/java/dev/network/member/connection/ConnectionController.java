package dev.network.member.connection;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConnectionController {
    private final ConnectionService service;

    public ConnectionController(ConnectionService s) {
        service = s;
    }

    @PostMapping("/api/v1/connections")
    public ConnectionService.View request(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Request r) {
        return service.request(jwt.getSubject(), r.targetId());
    }

    @PostMapping("/api/v1/connections/{id}/{action}")
    public ConnectionService.View command(
            @AuthenticationPrincipal Jwt jwt, @PathVariable String id, @PathVariable String action) {
        return service.command(jwt.getSubject(), id, action);
    }

    @GetMapping("/api/v1/connections")
    public List<ConnectionService.View> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "ACCEPTED") Connection.State state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(jwt.getSubject(), state, page, size);
    }

    @GetMapping("/internal/v1/connections/{memberId}")
    public List<String> accepted(@PathVariable String memberId) {
        return service.accepted(memberId);
    }

    public record Request(@NotBlank String targetId) {
    }
}
