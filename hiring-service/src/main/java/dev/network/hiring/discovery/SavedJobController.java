package dev.network.hiring.discovery;

import dev.network.hiring.shared.HiringPages;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class SavedJobController {
    private final SavedJobService service;

    public SavedJobController(SavedJobService service) {
        this.service = service;
    }

    @PutMapping("/api/v1/jobs/{id}/saved")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        service.save(j.getSubject(), id);
    }

    @DeleteMapping("/api/v1/jobs/{id}/saved")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void unsave(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        service.unsave(j.getSubject(), id);
    }

    @GetMapping("/api/v1/jobs/saved")
    public HiringPages.Slice<SavedJobService.Saved> list(
            @AuthenticationPrincipal Jwt j,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(j.getSubject(), cursor, size);
    }
}
