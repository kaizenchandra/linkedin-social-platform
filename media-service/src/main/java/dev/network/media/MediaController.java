package dev.network.media;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/media")
public class MediaController {
    private final MediaService service;

    public MediaController(MediaService s) {
        service = s;
    }

    @PostMapping(consumes = "multipart/form-data")
    public MediaService.View upload(
            @AuthenticationPrincipal Jwt j, @RequestPart("file") MultipartFile file) throws IOException {
        try (var input = file.getInputStream()) {
            return service.upload(j.getSubject(), input);
        }
    }

    @PostMapping("/attachments")
    public MediaService.OwnerState attach(
            @AuthenticationPrincipal Jwt j,
            @jakarta.validation.Valid @RequestBody MediaService.Attachment a) {
        return service.attach(j.getSubject(), a);
    }

    @GetMapping("/{id}")
    public MediaService.View metadata(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
        return service.own(j.getSubject(), id);
    }

    @GetMapping("/{id}/content")
    public void download(
            @AuthenticationPrincipal Jwt j, @PathVariable String id, HttpServletResponse response)
            throws IOException {
        var m = service.authorize(j.getSubject(), id);
        response.setContentType(m.contentType);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Disposition", "inline");
        service.stream(m, response.getOutputStream());
    }
}
