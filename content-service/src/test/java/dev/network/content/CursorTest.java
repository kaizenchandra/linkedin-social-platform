package dev.network.content;

import static org.assertj.core.api.Assertions.*;

import dev.network.content.feed.Cursor;

import java.time.*;

import org.junit.jupiter.api.Test;

class CursorTest {
    @Test
    void roundTripsTimestampAndTieBreaker() {
        var c =
                new Cursor(
                        Instant.parse("2026-01-01T00:00:00.123456Z"), "00000000-0000-0000-0000-000000000001");
        assertThat(Cursor.parse(c.encode(), Clock.systemUTC())).isEqualTo(c);
    }

    @Test
    void malformedCursorRejected() {
        assertThatThrownBy(() -> Cursor.parse("malformed", Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
