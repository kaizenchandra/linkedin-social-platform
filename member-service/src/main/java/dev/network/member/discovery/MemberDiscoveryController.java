package dev.network.member.discovery;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class MemberDiscoveryController {
    private final MemberDiscovery service;

    public MemberDiscoveryController(MemberDiscovery service) {
        this.service = service;
    }

    @GetMapping("/api/v1/members/suggestions")
    public List<MemberDiscovery.Suggestion> suggest(
            @AuthenticationPrincipal Jwt j, @RequestParam(defaultValue = "10") int size) {
        return service.suggest(j.getSubject(), size);
    }
}
