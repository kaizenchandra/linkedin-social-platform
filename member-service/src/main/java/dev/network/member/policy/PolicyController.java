package dev.network.member.policy;

import dev.network.web.Pages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class PolicyController {
    private final PolicyService policy;
    private final BlockRepository blocks;

    public PolicyController(PolicyService p, BlockRepository b) {
        policy = p;
        blocks = b;
    }

    @PostMapping("/internal/v1/policy")
    public List<PolicyService.Decision> check(@Valid @RequestBody Check request) {
        return policy.check(request.actorId(), request.memberIds());
    }

    @PutMapping("/api/v1/blocks/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        policy.block(j.getSubject(), id);
    }

    @DeleteMapping("/api/v1/blocks/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        policy.unblock(j.getSubject(), id);
    }

    @GetMapping("/api/v1/blocks")
    public List<String> list(
            @AuthenticationPrincipal Jwt j,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return blocks
                .findByBlockerIdOrderByCreatedAtDescIdDesc(
                        j.getSubject(), PageRequest.of(Pages.page(page), Pages.size(size)))
                .stream()
                .map(b -> b.blockedId)
                .toList();
    }

    public record Check(@NotBlank String actorId, @NotNull @Size(max = 501) List<String> memberIds) {
    }
}
