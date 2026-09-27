package dev.network.content.feed;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public record Cursor(Instant time, String id) {
    public static Cursor parse(String value, Clock clock) {
        if (value == null) return new Cursor(clock.instant(), "ffffffff-ffff-ffff-ffff-ffffffffffff");
        if (value.length() > 200) throw new IllegalArgumentException("Invalid cursor");
        try {
            String[] parts =
                    new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            UUID.fromString(parts[1]);
            return new Cursor(Instant.parse(parts[0]), parts[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor");
        }
    }

    public String encode() {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((time + "|" + id).getBytes(StandardCharsets.UTF_8));
    }
}
