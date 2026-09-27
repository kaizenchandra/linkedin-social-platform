package dev.network.hiring.application;

import dev.network.hiring.shared.HiringPages;
import jakarta.validation.Valid;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ApplicationController {
    private final ApplicationService service;

    public ApplicationController(ApplicationService s) {
        service = s;
    }

    @PostMapping("/applications")
    public ApplicationService.View submit(
            @AuthenticationPrincipal Jwt j, @Valid @RequestBody ApplicationService.Input in) {
        return service.submit(j.getSubject(), in);
    }

    @GetMapping("/applications/{id}")
    public ApplicationService.View get(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        return service.get(j.getSubject(), id);
    }

    @PutMapping("/applications/{id}/status")
    public ApplicationService.View status(
            @AuthenticationPrincipal Jwt j,
            @PathVariable String id,
            @Valid @RequestBody ApplicationService.Review in) {
        return service.review(j.getSubject(), id, in);
    }

    @PostMapping("/applications/{id}/withdraw")
    public ApplicationService.View withdraw(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        return service.withdraw(j.getSubject(), id);
    }

    @GetMapping("/applications/{id}/history")
    public List<ApplicationService.History> history(
            @AuthenticationPrincipal Jwt j, @PathVariable String id) {
        return service.history(j.getSubject(), id);
    }

    @GetMapping("/applications")
    public HiringPages.Slice<ApplicationService.Summary> own(
            @AuthenticationPrincipal Jwt j,
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) ApplicationState status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(j.getSubject(), null, jobId, status, cursor, size);
    }

    @GetMapping("/companies/{companyId}/applications")
    public HiringPages.Slice<ApplicationService.Summary> company(
            @AuthenticationPrincipal Jwt j,
            @PathVariable String companyId,
            @RequestParam(required = false) String jobId,
            @RequestParam(required = false) ApplicationState status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(j.getSubject(), companyId, jobId, status, cursor, size);
    }
}
