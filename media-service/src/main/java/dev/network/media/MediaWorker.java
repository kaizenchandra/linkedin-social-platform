package dev.network.media;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MediaWorker {
  private final MediaRepository repo;
  private final MediaService service;
  private final Clock clock;
  private final long grace;
  private final MeterRegistry metrics;

  public MediaWorker(
      MediaRepository r,
      MediaService s,
      Clock c,
      @Value("${MEDIA_GRACE_SECONDS:86400}") long grace,
      MeterRegistry metrics) {
    if (grace < 600) throw new IllegalArgumentException("Grace must be at least600 seconds");
    repo = r;
    service = s;
    clock = c;
    this.grace = grace;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${MEDIA_CLEANUP_INTERVAL_MS:60000}")
  public void run() {
    var before = clock.instant().minusSeconds(grace);
    for (var m : repo.stale(before, PageRequest.of(0, 25)))
      try {
        service.reconcile(m.id, before);
      } catch (Exception e) {
        metrics.counter("media.reconciliation.failures").increment();
      }
  }
}
