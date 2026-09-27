package dev.network.notification;

import dev.network.web.stream.DurableStream;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Clock;

import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class StreamConfiguration {
    @Bean
    DurableStream durableStream(
            JdbcTemplate db, PlatformTransactionManager tm, Clock clock, MeterRegistry metrics) {
        return new DurableStream(db, tm, clock, "notifications", metrics);
    }

    @Bean
    dev.network.web.stream.LiveStream liveStream(
            DurableStream history,
            tools.jackson.databind.ObjectMapper json,
            Clock clock,
            MeterRegistry metrics,
            org.springframework.core.env.Environment env) {
        return new dev.network.web.stream.LiveStream(
                history,
                json,
                clock,
                metrics,
                env.getProperty("network.stream.max-connections", Integer.class, 32),
                env.getProperty("network.stream.per-owner", Integer.class, 3),
                java.time.Duration.ofSeconds(
                        env.getProperty("network.stream.lifetime-seconds", Long.class, 120L)),
                java.time.Duration.ofHours(
                        env.getProperty("network.stream.retention-hours", Long.class, 24L)));
    }
}
