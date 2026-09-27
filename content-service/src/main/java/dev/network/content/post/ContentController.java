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
    private final DiscoveryContentService discovery;

    public ContentController(ContentService s, MemberClient m, DiscoveryContentService discovery) {
        this.discovery = discovery;
        service = s;
        members = m;
    }

    @PostMapping("/posts")
    public ContentService.PostView create(
            @AuthenticationPrincipal Jwt j, @Valid @RequestBody PostInput in) {
        return service.create(j.getSubject(), in.body(), in.visibility());
    }

    @PutMapping("/posts/{id}")
    public ContentService.PostView edit(
            @AuthenticationPrincipal Jwt j, @PathVariable String id, @Valid @RequestBody PostInput in) {
        return service.edit(j.getSubject(), id, in.body(), in.visibility());
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        service.delete(j.getSubject(), id);
    }

    @GetMapping("/posts/{id}")
    public ContentService.Detail get(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        return service.detail(j.getSubject(), id);
    }

    @GetMapping("/posts")
    public Pages.Slice<ContentService.PostView> posts(
            @AuthenticationPrincipal Jwt j,
            @RequestParam String authorId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        UUID.fromString(authorId);
        return service.timeline(j.getSubject(), List.of(authorId), cursor, size);
    }

    @GetMapping("/feed")
    public Pages.Slice<ContentService.PostView> feed(
            @AuthenticationPrincipal Jwt j,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return discovery.feed(j.getSubject(), cursor, size);
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
            @AuthenticationPrincipal Jwt j,
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.comments(j.getSubject(), id, page, size);
    }

    @DeleteMapping("/comments/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteComment(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        service.deleteComment(j.getSubject(), id);
    }

    public record PostInput(@NotBlank @Size(max = 3000) String body, Post.Visibility visibility) {
    }

    public record CommentInput(@NotBlank @Size(max = 1000) String body) {
    }
}
