package dev.network.hiring.alerts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
public class AlertInternalController {
    private final AlertMatcher matcher;
    private final SavedSearchService searches;

    public AlertInternalController(AlertMatcher matcher, SavedSearchService searches) {
        this.matcher = matcher;
        this.searches = searches;
    }

    @PostMapping("/internal/v1/hiring/alerts/eligible")
    public boolean eligible(@Valid @RequestBody Eligibility in) {
        return matcher.eligible(in.memberId(), in.matchId(), in.jobId());
    }

    @PostMapping("/internal/v1/hiring/alerts/consent")
    public boolean consent(@Valid @RequestBody Member in) {
        return searches.consent(in.memberId());
    }

    @PostMapping("/api/v1/hiring/moderation/alerts/{jobId}/replay")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void replay(@PathVariable String jobId) {
        matcher.replay(jobId);
    }

    public record Eligibility(
            @NotBlank String memberId, @NotBlank String matchId, @NotBlank String jobId) {
    }

    public record Member(@NotBlank String memberId) {
    }
}
