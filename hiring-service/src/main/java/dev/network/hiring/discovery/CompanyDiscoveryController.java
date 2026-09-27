package dev.network.hiring.discovery;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class CompanyDiscoveryController {
    private final CompanyDiscovery service;

    public CompanyDiscoveryController(CompanyDiscovery service) {
        this.service = service;
    }

    @GetMapping("/api/v1/companies/suggestions")
    public List<CompanyDiscovery.Suggestion> suggest(
            @AuthenticationPrincipal Jwt j,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "10") int size) {
        return service.suggest(j.getSubject(), industry, location, size);
    }
}
