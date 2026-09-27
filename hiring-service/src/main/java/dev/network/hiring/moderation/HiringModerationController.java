package dev.network.hiring.moderation;

import dev.network.hiring.shared.HiringPages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/hiring")
public class HiringModerationController {
    private final HiringModeration service;

    public HiringModerationController(HiringModeration service) {
        this.service = service;
    }

    @PostMapping("/reports")
    public HiringModeration.Report submit(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Input in) {
        return service.submit(jwt.getSubject(), in.jobId(), in.reason(), in.explanation());
    }

    @GetMapping("/reports")
    public HiringPages.Slice<HiringModeration.Report> own(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size) {
        return service.own(jwt.getSubject(), cursor, size);
    }

    @GetMapping("/moderation/reports")
    public HiringPages.Slice<HiringModeration.Report> queue(@RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size) {
        return service.queue(cursor, size);
    }

    @PostMapping("/moderation/reports/{id}/inspect")
    public HiringModeration.Inspection inspect(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody Inspection in) {
        return service.inspect(jwt.getSubject(), id, in.reason());
    }

    @PostMapping("/moderation/reports/{id}/actions")
    public HiringModeration.Report act(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody Command in) {
        return service.act(jwt.getSubject(), id, in.action(), in.reason());
    }

    @GetMapping("/moderation/reports/{id}/audit")
    public List<HiringModeration.Audit> history(@PathVariable String id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.history(id, page, size);
    }

    public record Input(@NotBlank String jobId, @NotNull HiringModeration.Reason reason,
                        @Size(max = 1000) String explanation) {
    }

    public record Inspection(@NotBlank @Size(max = 1000) String reason) {
    }

    public record Command(@NotNull HiringModeration.Action action, @NotBlank @Size(max = 1000) String reason) {
    }
}
