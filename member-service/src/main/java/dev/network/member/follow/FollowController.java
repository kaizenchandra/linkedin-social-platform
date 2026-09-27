package dev.network.member.follow;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class FollowController {
  private final FollowService service;

  public FollowController(FollowService service) {
    this.service = service;
  }

  @PutMapping("/api/v1/members/{id}/follow")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void follow(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    service.follow(j.getSubject(), id);
  }

  @DeleteMapping("/api/v1/members/{id}/follow")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void unfollow(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    service.unfollow(j.getSubject(), id);
  }

  @GetMapping("/api/v1/members/{id}/follow")
  public Map<String, Boolean> status(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    return Map.of("following", service.status(j.getSubject(), id));
  }

  @GetMapping("/api/v1/members/me/following")
  public List<FollowService.Follow> list(
      @AuthenticationPrincipal Jwt j,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(j.getSubject(), page, size);
  }

  @GetMapping("/internal/v1/follows/{id}")
  public List<String> ids(@PathVariable String id) {
    return service.ids(id);
  }
}
