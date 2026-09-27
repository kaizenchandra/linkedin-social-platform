package dev.network.web;

import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
@Configuration
public class SecurityConfiguration {
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  JwtDecoder decoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks,
      @Value("${network.audience}") String audience) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            jwt -> {
              boolean valid =
                  jwt.getAudience().contains(audience)
                      && jwt.getExpiresAt() != null
                      && jwt.getSubject() != null;
              try {
                UUID.fromString(jwt.getSubject());
              } catch (Exception e) {
                valid = false;
              }
              return valid
                  ? OAuth2TokenValidatorResult.success()
                  : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
            }));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity h) throws Exception {
    return h.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/actuator/prometheus")
                    .hasAuthority("SCOPE_metrics.read")
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .requestMatchers("/internal/**")
                    .hasAuthority("SCOPE_connections.read")
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint((req, res, error) -> problem(res, 401, "Unauthorized"))
                    .accessDeniedHandler((req, res, error) -> problem(res, 403, "Forbidden")))
        .oauth2ResourceServer(
            o ->
                o.jwt(j -> {})
                    .authenticationEntryPoint(
                        (req, res, error) -> problem(res, 401, "Unauthorized")))
        .build();
  }

  private static void problem(
      jakarta.servlet.http.HttpServletResponse response, int status, String title)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    response
        .getWriter()
        .write("{\"type\":\"about:blank\",\"status\":" + status + ",\"title\":\"" + title + "\"}");
  }
}
