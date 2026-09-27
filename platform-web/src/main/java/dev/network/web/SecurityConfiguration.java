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
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
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
                    .requestMatchers("/api/v1/moderation/**", "/api/v1/hiring/moderation/**")
                    .hasRole("moderator")
                    .requestMatchers(
                        "/internal/v1/hiring/member-exists", "/internal/v1/hiring/profile-snapshot")
                    .hasAuthority("SCOPE_hiring.profiles")
                    .requestMatchers(
                        "/internal/v1/hiring/recipients", "/internal/v1/hiring/alerts/**")
                    .hasAuthority("SCOPE_hiring.recipients")
                    .requestMatchers("/internal/v1/media/**")
                    .hasAuthority("SCOPE_media.manage")
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
                o.jwt(j -> j.jwtAuthenticationConverter(authenticationConverter()))
                    .authenticationEntryPoint(
                        (req, res, error) -> problem(res, 401, "Unauthorized")))
        .build();
  }

  private org.springframework.security.oauth2.server.resource.authentication
          .JwtAuthenticationConverter
      authenticationConverter() {
    var converter =
        new org.springframework.security.oauth2.server.resource.authentication
            .JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          var scopes =
              new org.springframework.security.oauth2.server.resource.authentication
                  .JwtGrantedAuthoritiesConverter();
          var authorities =
              new java.util.ArrayList<org.springframework.security.core.GrantedAuthority>(
                  scopes.convert(jwt));
          Object claim = jwt.getClaim("realm_access");
          if (claim instanceof java.util.Map<?, ?> realm
              && realm.get("roles") instanceof java.util.List<?> roles
              && roles.contains("moderator"))
            authorities.add(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                    "ROLE_moderator"));
          return authorities;
        });
    return converter;
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
