package dev.network.content.post;

import dev.network.content.feed.MemberClient;
import dev.network.web.Pages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ContentController {
  private final ContentService service;
  private final MemberClient members;

  public ContentController(ContentService s, MemberClient m) {
    service = s;
    members = m;
  }

  public record PostInput(@NotBlank @Size(max = 3000) String body) {}

  public record CommentInput(@NotBlank @Size(max = 1000) String body) {}

  @PostMapping("/posts")
  public ContentService.PostView create(
      @AuthenticationPrincipal Jwt j, @Valid @RequestBody PostInput in) {
    return service.create(j.getSubject(), in.body());
  }

  @PutMapping("/posts/{id}")
  public ContentService.PostView edit(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @Valid @RequestBody PostInput in) {
    return service.edit(j.getSubject(), id, in.body());
  }

  @DeleteMapping("/posts/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    service.delete(j.getSubject(), id);
  }

  @GetMapping("/posts/{id}")
  public ContentService.Detail get(@PathVariable String id) {
    return service.detail(id);
  }

  @GetMapping("/posts")
  public Pages.Slice<ContentService.PostView> posts(
      @RequestParam String authorId,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    UUID.fromString(authorId);
    return service.timeline(List.of(authorId), cursor, size);
  }

  @GetMapping("/feed")
  public Pages.Slice<ContentService.PostView> feed(
      @AuthenticationPrincipal Jwt j,
      @RequestParam(required = false) String cursor,
      @RequestParam(defaultValue = "20") int size) {
    var authors = new ArrayList<>(members.accepted(j.getSubject()));
    authors.add(j.getSubject());
    return service.timeline(authors, cursor, size);
  }

  @PutMapping("/posts/{id}/like")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void like(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    service.like(j.getSubject(), id);
  }

  @DeleteMapping("/posts/{id}/like")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void unlike(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    service.unlike(j.getSubject(), id);
  }

  @PostMapping("/posts/{id}/comments")
  public ContentService.CommentView comment(
      @AuthenticationPrincipal Jwt j,
      @PathVariable String id,
      @Valid @RequestBody CommentInput in) {
    return service.comment(j.getSubject(), id, in.body());
  }

  @GetMapping("/posts/{id}/comments")
  public List<ContentService.CommentView> comments(
      @PathVariable String id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.comments(id, page, size);
  }

  @DeleteMapping("/comments/{id}")
  @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
  public void deleteComment(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    service.deleteComment(j.getSubject(), id);
  }
}
