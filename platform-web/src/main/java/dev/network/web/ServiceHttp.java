package dev.network.web;

import java.net.http.HttpClient;
import java.time.*;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bounded client-credentials transport. No resource authorization decisions live here.
 */
@Component
public class ServiceHttp {
    private final RestClient http;
    private final String tokenUrl, clientId, secret;
    private final Clock clock;
    private String token;
    private Instant expires = Instant.EPOCH;

    public ServiceHttp(
            RestClient.Builder builder,
            Clock clock,
            @Value("${OIDC_TOKEN_URL:http://localhost:8180/realms/network/protocol/openid-connect/token}")
            String tokenUrl,
            @Value("${INTERNAL_CLIENT_ID:unused}") String clientId,
            @Value("${INTERNAL_CLIENT_SECRET:unused}") String secret) {
        this.clock = clock;
        this.tokenUrl = tokenUrl;
        this.clientId = clientId;
        this.secret = secret;
        var f =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        f.setReadTimeout(Duration.ofSeconds(5));
        http = builder.requestFactory(f).build();
    }

    private synchronized String token() {
        if (clock.instant().isBefore(expires)) return token;
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        var r =
                http.post()
                        .uri(tokenUrl)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(Map.class);
        token = (String) Objects.requireNonNull(r).get("access_token");
        expires = clock.instant().plusSeconds(((Number) r.get("expires_in")).longValue() - 30);
        return token;
    }

    public <T> T post(String url, Object body, Class<T> type) {
        try {
            return http.post()
                    .uri(url)
                    .headers(h -> h.setBearerAuth(token()))
                    .body(body)
                    .retrieve()
                    .body(type);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404
                    || e.getStatusCode().value() == 409
                    || e.getStatusCode().value() == 400)
                throw new ResponseStatusException(e.getStatusCode(), "Resource operation rejected");
            throw unavailable();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE, "Required service unavailable; retry request");
    }
}
