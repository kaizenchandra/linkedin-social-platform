package dev.network.api;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.*;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class GatewayConfiguration {
  @Bean
  org.springframework.cloud.gateway.config.HttpClientCustomizer dnsLifetime() {
    return client ->
        client.resolver(
            r ->
                r.cacheMaxTimeToLive(java.time.Duration.ofSeconds(5))
                    .cacheMinTimeToLive(java.time.Duration.ZERO));
  }

  @Bean
  RouteLocator routes(
      RouteLocatorBuilder b,
      @Value("${MEMBER_URL:http://localhost:8081}") String m,
      @Value("${CONTENT_URL:http://localhost:8082}") String c,
      @Value("${NOTIFICATION_URL:http://localhost:8083}") String n,
      @Value("${MEDIA_URL:http://localhost:8084}") String media,
      @Value("${MESSAGING_URL:http://localhost:8085}") String messaging,
      @Value("${HIRING_URL:http://localhost:8086}") String hiring) {
    return b.routes()
        .route(
            "messaging-stream",
            r ->
                r.path("/api/v1/conversations/stream")
                    .metadata("response-timeout", -1)
                    .uri(messaging))
        .route(
            "notification-stream",
            r -> r.path("/api/v1/notifications/stream").metadata("response-timeout", -1).uri(n))
        .route(
            "hiring",
            r ->
                r.path(
                        "/api/v1/companies/**",
                        "/api/v1/company-invitations/**",
                        "/api/v1/jobs/**",
                        "/api/v1/applications/**",
                        "/api/v1/hiring/**")
                    .uri(hiring))
        .route("messaging", r -> r.path("/api/v1/conversations/**").uri(messaging))
        .route(
            "media",
            r ->
                r.path("/api/v1/media/**")
                    .filters(
                        f ->
                            f.setRequestSize(org.springframework.util.unit.DataSize.ofMegabytes(6)))
                    .uri(media))
        .route(
            "members",
            r ->
                r.path("/api/v1/members/**", "/api/v1/connections/**", "/api/v1/blocks/**")
                    .filters(
                        f ->
                            f.setRequestSize(
                                org.springframework.util.unit.DataSize.ofKilobytes(64)))
                    .uri(m))
        .route(
            "content",
            r ->
                r.path(
                        "/api/v1/posts/**",
                        "/api/v1/feed",
                        "/api/v1/comments/**",
                        "/api/v1/reports/**",
                        "/api/v1/moderation/**")
                    .filters(
                        f ->
                            f.setRequestSize(
                                org.springframework.util.unit.DataSize.ofKilobytes(64)))
                    .uri(c))
        .route(
            "notifications",
            r ->
                r.path("/api/v1/notifications/**")
                    .filters(
                        f ->
                            f.setRequestSize(
                                org.springframework.util.unit.DataSize.ofKilobytes(64)))
                    .uri(n))
        .build();
  }

  @Bean
  GlobalFilter correlation() {
    return (exchange, chain) -> {
      String id = UUID.randomUUID().toString();
      var request =
          exchange
              .getRequest()
              .mutate()
              .headers(
                  h -> {
                    h.remove("X-User-Id");
                    h.remove("X-Member-Id");
                    h.remove("X-Roles");
                    h.set("X-Correlation-Id", id);
                  })
              .build();
      exchange.getResponse().getHeaders().set("X-Correlation-Id", id);
      return chain.filter(exchange.mutate().request(request).build());
    };
  }

  @Bean
  ReactiveJwtDecoder decoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks) {
    var d = NimbusReactiveJwtDecoder.withJwkSetUri(jwks).build();
    d.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            j ->
                j.getAudience().contains("network-api")
                        && j.getSubject() != null
                        && j.getExpiresAt() != null
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
    return d;
  }

  @Bean
  SecurityWebFilterChain security(
      ServerHttpSecurity h, @Value("${CORS_ORIGIN:http://localhost:3000}") String origin) {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(origin));
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Last-Event-ID"));
    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return h.csrf(c -> c.disable())
        .cors(c -> c.configurationSource(source))
        .authorizeExchange(
            a ->
                a.pathMatchers("/actuator/health/**")
                    .permitAll()
                    .pathMatchers("/actuator/prometheus")
                    .hasAuthority("SCOPE_metrics.read")
                    .pathMatchers("/actuator/**")
                    .denyAll()
                    .anyExchange()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (exchange, error) -> problem(exchange, 401, "Unauthorized"))
                    .accessDeniedHandler((exchange, error) -> problem(exchange, 403, "Forbidden")))
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> {})
                    .authenticationEntryPoint(
                        (exchange, error) -> problem(exchange, 401, "Unauthorized")))
        .build();
  }

  static reactor.core.publisher.Mono<Void> problem(
      org.springframework.web.server.ServerWebExchange exchange, int status, String title) {
    var response = exchange.getResponse();
    response.setStatusCode(org.springframework.http.HttpStatusCode.valueOf(status));
    response
        .getHeaders()
        .setContentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON);
    byte[] body =
        ("{\"type\":\"about:blank\",\"status\":" + status + ",\"title\":\"" + title + "\"}")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return response.writeWith(
        reactor.core.publisher.Mono.just(response.bufferFactory().wrap(body)));
  }
}
