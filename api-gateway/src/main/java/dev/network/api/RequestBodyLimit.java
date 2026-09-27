package dev.network.api;

import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.*;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.*;

@Component
public class RequestBodyLimit implements GlobalFilter, Ordered {
  public int getOrder() {
    return -100;
  }

  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return DataBufferUtils.join(exchange.getRequest().getBody(), 65536)
        .map(
            buffer -> {
              byte[] bytes = new byte[buffer.readableByteCount()];
              buffer.read(bytes);
              DataBufferUtils.release(buffer);
              return bytes;
            })
        .defaultIfEmpty(new byte[0])
        .flatMap(
            bytes -> {
              var request =
                  new ServerHttpRequestDecorator(exchange.getRequest()) {
                    @Override
                    public Flux<DataBuffer> getBody() {
                      return bytes.length == 0
                          ? Flux.empty()
                          : Flux.defer(
                              () -> Flux.just(exchange.getResponse().bufferFactory().wrap(bytes)));
                    }

                    @Override
                    public HttpHeaders getHeaders() {
                      var headers = new HttpHeaders();
                      headers.putAll(super.getHeaders());
                      headers.remove(HttpHeaders.TRANSFER_ENCODING);
                      headers.setContentLength(bytes.length);
                      return headers;
                    }
                  };
              return chain.filter(exchange.mutate().request(request).build());
            })
        .onErrorResume(
            DataBufferLimitException.class,
            e -> {
              exchange.getResponse().setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
              exchange
                  .getResponse()
                  .getHeaders()
                  .setContentType(MediaType.APPLICATION_PROBLEM_JSON);
              return exchange
                  .getResponse()
                  .writeWith(
                      Mono.just(
                          exchange
                              .getResponse()
                              .bufferFactory()
                              .wrap(
                                  "{\"type\":\"about:blank\",\"status\":413,\"title\":\"Request too large\"}"
                                      .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            });
  }
}
