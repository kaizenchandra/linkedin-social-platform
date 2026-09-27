package dev.network.api;

import java.time.Duration;
import java.util.*;
import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.*;

/** Instance-local edge admission; each owning service also limits and authenticates streams. */
@Component
public class StreamAdmission
    implements GlobalFilter,
        Ordered,
        org.springframework.context.ApplicationListener<
            org.springframework.context.event.ContextClosedEvent> {
  private volatile boolean stopping;
  private final reactor.core.publisher.Sinks.Empty<Void> shutdown =
      reactor.core.publisher.Sinks.empty();

  public void onApplicationEvent(org.springframework.context.event.ContextClosedEvent event) {
    stopping = true;
    shutdown.tryEmitEmpty();
  }

  private final Map<String, Integer> owners = new HashMap<>();
  private int active;
  private final io.micrometer.core.instrument.MeterRegistry metrics;

  public StreamAdmission(io.micrometer.core.instrument.MeterRegistry metrics) {
    this.metrics = metrics;
    metrics.gauge("network.stream.gateway.active", this, x -> x.count());
  }

  private synchronized int count() {
    return active;
  }

  private synchronized boolean acquire(String owner) {
    if (active >= 64 || owners.getOrDefault(owner, 0) >= 6) return false;
    active++;
    owners.merge(owner, 1, Integer::sum);
    return true;
  }

  private synchronized void release(String owner) {
    active--;
    owners.computeIfPresent(owner, (k, v) -> v == 1 ? null : v - 1);
  }

  public int getOrder() {
    return -2;
  }

  public Mono<Void> filter(
      org.springframework.web.server.ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!Set.of("/api/v1/conversations/stream", "/api/v1/notifications/stream")
        .contains(exchange.getRequest().getPath().value())) return chain.filter(exchange);
    return exchange
        .getPrincipal()
        .flatMap(
            principal -> {
              String owner = principal.getName();
              if (stopping) return GatewayConfiguration.problem(exchange, 503, "Gateway draining");
              if (!acquire(owner)) {
                metrics.counter("network.stream.gateway.rejected").increment();
                return GatewayConfiguration.problem(exchange, 429, "Instance stream limit reached");
              }
              StreamTransport.bound(
                  ServerHttpResponseDecorator.getNativeResponse(exchange.getResponse()), metrics);
              var response =
                  new ServerHttpResponseDecorator(exchange.getResponse()) {
                    @Override
                    public Mono<Void> writeAndFlushWith(
                        org.reactivestreams.Publisher<
                                ? extends
                                    org.reactivestreams.Publisher<
                                        ? extends org.springframework.core.io.buffer.DataBuffer>>
                            body) {
                      return super.writeAndFlushWith(
                          Flux.from(body).limitRate(1).map(group -> Flux.from(group).limitRate(1)));
                    }

                    @Override
                    public Mono<Void> writeWith(
                        org.reactivestreams.Publisher<
                                ? extends org.springframework.core.io.buffer.DataBuffer>
                            body) {
                      return super.writeWith(Flux.from(body).limitRate(1));
                    }
                  };
              return chain
                  .filter(exchange.mutate().response(response).build())
                  .timeout(Duration.ofSeconds(130))
                  .takeUntilOther(shutdown.asMono())
                  .doFinally(signal -> release(owner));
            });
  }
}
