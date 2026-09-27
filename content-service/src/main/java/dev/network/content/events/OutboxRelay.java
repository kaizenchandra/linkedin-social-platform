package dev.network.content.events;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.*;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "network.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {
  private final EntityManager em;
  private final KafkaTemplate<String, String> kafka;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final MeterRegistry metrics;

  public OutboxRelay(
      EntityManager em,
      KafkaTemplate<String, String> kafka,
      PlatformTransactionManager tm,
      Clock clock,
      MeterRegistry metrics) {
    this.em = em;
    this.kafka = kafka;
    this.tx = new TransactionTemplate(tm);
    this.clock = clock;
    this.metrics = metrics;
  }

  @Scheduled(fixedDelayString = "${network.outbox.delay:1000}")
  public void relay() {
    tx.executeWithoutResult(
        status -> {
          @SuppressWarnings("unchecked")
          var rows =
              (java.util.List<Outbox>)
                  em.createNativeQuery(
                          "SELECT * FROM outbox WHERE delivered_at IS NULL AND (next_attempt_at IS"
                              + " NULL OR next_attempt_at<=SYSTIMESTAMP) AND ROWNUM<=5 FOR UPDATE"
                              + " SKIP LOCKED",
                          Outbox.class)
                      .getResultList();
          for (var row : rows) {
            Context context = Context.root();
            if (row.traceParent != null
                && row.traceParent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) {
              var p = row.traceParent.split("-");
              context =
                  context.with(
                      Span.wrap(
                          SpanContext.createFromRemoteParent(
                              p[1], p[2], TraceFlags.fromHex(p[3], 0), TraceState.getDefault())));
            }
            try (Scope scope = context.makeCurrent()) {
              kafka
                  .send("network.events.v1", row.aggregateId, row.envelope)
                  .get(12, TimeUnit.SECONDS);
              row.deliveredAt = clock.instant();
              metrics.counter("outbox.delivered").increment();
            } catch (Exception e) {
              row.attempts++;
              row.nextAttemptAt =
                  clock.instant().plusSeconds(Math.min(60, 1L << Math.min(row.attempts, 5)));
              metrics.counter("outbox.failures").increment();
              if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            }
          }
        });
  }

  @Scheduled(fixedDelay = 10000)
  public void backlog() {
    Number pending =
        (Number)
            em.createNativeQuery("SELECT COUNT(*) FROM outbox WHERE delivered_at IS NULL")
                .getSingleResult();
    backlog = pending.longValue();
  }

  private volatile long backlog;

  @jakarta.annotation.PostConstruct
  void register() {
    metrics.gauge("outbox.backlog", this, r -> r.backlog);
  }
}
