package dev.network.web.stream;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Bounded servlet transport; Oracle history, rather than this process, owns delivery recovery. */
public final class LiveStream implements AutoCloseable, ApplicationListener<ContextClosedEvent> {
  private final DurableStream history;
  private final ObjectMapper json;
  private final Clock clock;
  private final MeterRegistry metrics;
  private final int maximum, perOwner;
  private final Duration lifetime, retention;
  private final Map<Session, Boolean> sessions = new ConcurrentHashMap<>();
  private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
  private final ThreadPoolExecutor polls =
      new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(32));
  private volatile boolean stopping;
  private final Map<String, Integer> admittedOwners = new HashMap<>();
  private int admitted;

  public LiveStream(
      DurableStream history,
      ObjectMapper json,
      Clock clock,
      MeterRegistry metrics,
      int maximum,
      int perOwner,
      Duration lifetime,
      Duration retention) {
    if (maximum < 1
        || maximum > 128
        || perOwner < 1
        || perOwner > maximum
        || lifetime.isNegative()
        || lifetime.isZero()
        || lifetime.compareTo(Duration.ofMinutes(5)) > 0
        || retention.isNegative()
        || retention.isZero()) throw new IllegalArgumentException("Invalid stream bounds");
    this.history = history;
    this.json = json;
    this.clock = clock;
    this.metrics = metrics;
    this.maximum = maximum;
    this.perOwner = perOwner;
    this.lifetime = lifetime;
    this.retention = retention;
    metrics.gauge("network.stream.active", sessions, Map::size);
    metrics.gauge("network.stream.poll.queue", polls, p -> p.getQueue().size());
    metrics.gauge(
        "network.stream.oldest.pending.seconds",
        this,
        x -> x.sessions.keySet().stream().mapToDouble(Session::pendingAge).max().orElse(0));
    timer.scheduleWithFixedDelay(
        () -> sessions.keySet().forEach(Session::tick), 100, 100, TimeUnit.MILLISECONDS);
    timer.scheduleWithFixedDelay(
        () -> {
          try {
            polls.execute(
                () -> {
                  try {
                    history.cleanup(retention);
                  } catch (Exception e) {
                    metrics.counter("network.stream.cleanup.failures").increment();
                  }
                });
          } catch (RejectedExecutionException e) {
            metrics.counter("network.stream.cleanup.deferred").increment();
          }
        },
        60,
        60,
        TimeUnit.SECONDS);
  }

  private synchronized void reserve(String owner) {
    if (stopping)
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Stream instance draining");
    if (admitted >= maximum || admittedOwners.getOrDefault(owner, 0) >= perOwner) {
      metrics.counter("network.stream.admission.rejected").increment();
      throw new ResponseStatusException(
          HttpStatus.TOO_MANY_REQUESTS, "Instance stream limit reached");
    }
    admitted++;
    admittedOwners.merge(owner, 1, Integer::sum);
  }

  private synchronized void release(String owner) {
    admitted--;
    admittedOwners.computeIfPresent(owner, (key, value) -> value == 1 ? null : value - 1);
  }

  public void open(
      String owner,
      Instant expires,
      String cursor,
      HttpServletRequest request,
      HttpServletResponse response)
      throws IOException {
    if (expires == null || !expires.isAfter(clock.instant()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired");
    reserve(owner);
    Session session = null;
    AsyncContext async = null;
    try {
      var initial = history.replay(owner, cursor, 50);
      response.setContentType("text/event-stream");
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Cache-Control", "no-store, no-transform");
      response.setHeader("X-Accel-Buffering", "no");
      response.setBufferSize(4096);
      synchronized (this) {
        if (stopping)
          throw new ResponseStatusException(
              HttpStatus.SERVICE_UNAVAILABLE, "Stream instance draining");
        async = request.startAsync(request, response);
        Instant end = clock.instant().plus(lifetime);
        if (expires.isBefore(end)) end = expires;
        session = new Session(owner, cursor, expires, end, async, response.getOutputStream());
        sessions.put(session, Boolean.TRUE);
      }
      Session active = session;
      async.setTimeout(Math.max(1, Duration.between(clock.instant(), active.end).toMillis()));
      async.addListener(
          new AsyncListener() {
            public void onComplete(AsyncEvent e) {
              active.finish("client_closed");
            }

            public void onError(AsyncEvent e) {
              active.finish("io_error");
            }

            public void onTimeout(AsyncEvent e) {
              active.finish(!clock.instant().isBefore(expires) ? "token_expired" : "lifetime");
            }

            public void onStartAsync(AsyncEvent e) {}
          });
      active.offer(initial);
      active.output.setWriteListener(
          new WriteListener() {
            public void onWritePossible() {
              active.write();
            }

            public void onError(Throwable e) {
              active.finish("io_error");
            }
          });
    } catch (IOException | RuntimeException e) {
      if (session != null) session.finish("io_error");
      else {
        release(owner);
        if (async != null) async.complete();
      }
      throw e;
    }
  }

  private final class Session {
    final String owner;
    final Instant expires, end;
    final AsyncContext async;
    final ServletOutputStream output;
    String cursor, nextCursor;
    byte[] pending;
    Instant pendingAt, oldestEvent, pollStarted;
    List<DurableStream.Event> batch = List.of();
    int offset, emitted;
    boolean closed, polling;
    Instant nextPoll = Instant.MIN, lastWrite = Instant.MIN;

    Session(
        String owner,
        String cursor,
        Instant expires,
        Instant end,
        AsyncContext async,
        ServletOutputStream output) {
      this.owner = owner;
      this.cursor = cursor;
      this.expires = expires;
      this.end = end;
      this.async = async;
      this.output = output;
    }

    synchronized double pendingAge() {
      return oldestEvent == null
          ? 0
          : Math.max(0, Duration.between(oldestEvent, clock.instant()).toMillis() / 1000.0);
    }

    synchronized void offer(List<DurableStream.Event> events) {
      if (closed) return;
      if (pending != null) throw new IllegalStateException("Only one outstanding batch allowed");
      StringBuilder text = new StringBuilder();
      for (var event : events)
        text.append("id: ")
            .append(event.cursor())
            .append("\nevent: ")
            .append(event.eventType())
            .append("\ndata: ")
            .append(json.writeValueAsString(event))
            .append("\n\n");
      if (events.isEmpty()) text.append(": heartbeat\n\n");
      byte[] bytes = text.toString().getBytes(StandardCharsets.UTF_8);
      if (bytes.length > 32768) {
        finish("frame_limit");
        return;
      }
      pending = bytes;
      offset = 0;
      batch = events;
      pendingAt = clock.instant();
      oldestEvent = events.isEmpty() ? null : events.getFirst().occurredAt();
      nextCursor = events.isEmpty() ? cursor : events.getLast().cursor();
    }

    synchronized void write() {
      if (closed) return;
      if (!clock.instant().isBefore(end)) {
        finish(!clock.instant().isBefore(expires) ? "token_expired" : "lifetime");
        return;
      }
      try {
        while (pending != null && output.isReady()) {
          int size = Math.min(4096, pending.length - offset);
          output.write(pending, offset, size);
          offset += size;
          if (offset == pending.length) {
            output.flush();
            cursor = nextCursor;
            pending = null;
            oldestEvent = null;
            lastWrite = clock.instant();
            metrics.counter("network.stream.events.emitted").increment(batch.size());
            for (var event : batch)
              metrics
                  .timer("network.stream.emission.lag")
                  .record(
                      Duration.ofMillis(
                          Math.max(
                              0,
                              Duration.between(event.occurredAt(), clock.instant()).toMillis())));
            emitted += batch.size();
            nextPoll = clock.instant().plusMillis(batch.size() == 50 ? 100 : 1000);
            batch = List.of();
            if (emitted >= 5000) {
              finish("replay_budget");
              return;
            }
          }
        }
      } catch (Exception e) {
        finish("io_error");
      }
    }

    synchronized void tick() {
      if (closed) return;
      Instant now = clock.instant();
      if (!now.isBefore(end)) {
        finish(!now.isBefore(expires) ? "token_expired" : "lifetime");
        return;
      }
      if (polling && Duration.between(pollStarted, now).compareTo(Duration.ofSeconds(5)) >= 0) {
        finish("database_timeout");
        return;
      }
      if (pending != null) {
        if (Duration.between(pendingAt, now).compareTo(Duration.ofSeconds(5)) >= 0)
          finish("slow_client");
        return;
      }
      if (!polling && !now.isBefore(nextPoll)) {
        polling = true;
        pollStarted = now;
        nextPoll = now.plusSeconds(1);
        try {
          polls.execute(this::poll);
        } catch (RejectedExecutionException e) {
          finish("poll_capacity");
        }
      }
    }

    void poll() {
      String after;
      synchronized (this) {
        if (closed) return;
        after = cursor;
      }
      try {
        var events = history.replay(owner, after, 50);
        synchronized (this) {
          polling = false;
          if (closed) return;
          if (!events.isEmpty()
              || Duration.between(lastWrite, clock.instant()).compareTo(Duration.ofSeconds(10))
                  >= 0) {
            offer(events);
            write();
          }
        }
      } catch (Exception e) {
        finish(
            e instanceof ResponseStatusException r && r.getStatusCode().value() == 410
                ? "replay_expired"
                : "database_error");
      }
    }

    synchronized void finish(String reason) {
      if (closed) return;
      closed = true;
      pending = null;
      batch = List.of();
      oldestEvent = null;
      sessions.remove(this);
      release(owner);
      metrics.counter("network.stream.disconnects", "reason", reason).increment();
      try {
        async.complete();
      } catch (IllegalStateException ignored) {
      }
    }
  }

  @Override
  public void onApplicationEvent(ContextClosedEvent event) {
    close();
  }

  @Override
  public void close() {
    synchronized (this) {
      if (stopping) return;
      stopping = true;
    }
    sessions.keySet().forEach(s -> s.finish("shutdown"));
    timer.shutdownNow();
    polls.shutdownNow();
  }
}
