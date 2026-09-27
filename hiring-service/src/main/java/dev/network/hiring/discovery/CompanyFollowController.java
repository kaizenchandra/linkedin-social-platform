package dev.network.hiring.discovery;

import dev.network.hiring.shared.HiringPages;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class CompanyFollowController {
    private final CompanyFollowService service;

    public CompanyFollowController(CompanyFollowService service) {
        this.service = service;
    }

    @PutMapping("/api/v1/companies/{id}/follow")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void follow(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        service.follow(j.getSubject(), id);
    }

    @DeleteMapping("/api/v1/companies/{id}/follow")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void unfollow(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        service.unfollow(j.getSubject(), id);
    }

    @GetMapping("/api/v1/companies/{id}/follow")
    public Map<String, Boolean> status(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        return Map.of("following", service.status(j.getSubject(), id));
    }

    @GetMapping("/api/v1/companies/following")
    public HiringPages.Slice<CompanyFollowService.Follow> list(
            @AuthenticationPrincipal Jwt j,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(j.getSubject(), cursor, size);
    }
}
