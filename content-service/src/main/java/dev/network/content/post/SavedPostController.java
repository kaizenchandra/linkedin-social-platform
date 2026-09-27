package dev.network.content.post;

import dev.network.web.Pages;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class SavedPostController {
  private final DiscoveryContentService service;

  public SavedPostController(DiscoveryContentService service) {
    this.service = service;
  }

  @PutMapping("/api/v1/posts/{id}/saved")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void save(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    service.save(j.getSubject(), id);
  }

  @DeleteMapping("/api/v1/posts/{id}/saved")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void unsave(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    service.unsave(j.getSubject(), id);
  }

  @GetMapping("/api/v1/posts/saved")
  public Pages.Slice<DiscoveryContentService.Saved> list(
      @AuthenticationPrincipal Jwt j,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    return service.saved(j.getSubject(), cursor, size);
  }
}
