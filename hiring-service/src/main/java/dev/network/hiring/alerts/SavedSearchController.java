package dev.network.hiring.alerts;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobs/searches")
public class SavedSearchController {
  private final SavedSearchService service;

  public SavedSearchController(SavedSearchService service) {
    this.service = service;
  }

  @PostMapping
  public SavedSearchService.View create(
      @AuthenticationPrincipal Jwt j, @Valid @RequestBody SavedSearchService.Input in) {
    return service.create(j.getSubject(), in);
  }

  @GetMapping
  public List<SavedSearchService.View> list(@AuthenticationPrincipal Jwt j) {
    return service.list(j.getSubject());
  }

  @PutMapping("/{id}")
  public SavedSearchService.View update(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @Valid @RequestBody SavedSearchService.Input in) {
    return service.update(j.getSubject(), id, in);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    service.delete(j.getSubject(), id);
  }
}
