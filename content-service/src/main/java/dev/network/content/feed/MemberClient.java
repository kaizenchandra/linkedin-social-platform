package dev.network.content.feed;

import java.net.http.HttpClient;
import java.time.*;
import java.util.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class MemberClient {
    private final RestClient client;
    private final String memberUrl, tokenUrl, secret;
    private final Clock clock;
    private String token;
    private Instant expires = Instant.EPOCH;

    public MemberClient(
            RestClient.Builder builder,
            Clock clock,
            @Value("${MEMBER_URL:http://localhost:8081}") String memberUrl,
            @Value("${OIDC_TOKEN_URL:http://localhost:8180/realms/network/protocol/openid-connect/token}")
            String tokenUrl,
            @Value("${CONTENT_CLIENT_SECRET}") String secret) {
        this.clock = clock;
        this.memberUrl = memberUrl;
        this.tokenUrl = tokenUrl;
        this.secret = secret;
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(3));
        client = builder.requestFactory(factory).build();
    }

    private synchronized String token() {
        if (clock.instant().isBefore(expires)) return token;
        var data = new LinkedMultiValueMap<String, String>();
        data.add("grant_type", "client_credentials");
        data.add("client_id", "content-internal");
        data.add("client_secret", secret);
        var response =
                client
                        .post()
                        .uri(tokenUrl)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(data)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {
                        });
        token = (String) Objects.requireNonNull(response).get("access_token");
        expires = clock.instant().plusSeconds(((Number) response.get("expires_in")).longValue() - 30);
        return token;
    }

    public Map<String, Decision> policy(String actor, List<String> targets) {
        if (targets.isEmpty()) return Map.of();
        try {
            var result =
                    client
                            .post()
                            .uri(memberUrl + "/internal/v1/policy")
                            .headers(h -> h.setBearerAuth(token()))
                            .body(Map.of("actorId", actor, "memberIds", targets))
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<Decision>>() {
                            });
            if (result == null) throw new IllegalStateException();
            var decisions = new HashMap<String, Decision>();
            for (var d : result) decisions.put(d.memberId(), d);
            if (!decisions.keySet().containsAll(targets)) throw new IllegalStateException();
            return decisions;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Current privacy policy unavailable; retry request");
        }
    }

    public List<String> accepted(String member) {
        return relationships(member, "connections");
    }

    public List<String> followed(String member) {
        return relationships(member, "follows");
    }

    private List<String> relationships(String member, String kind) {
        try {
            var ids =
                    client
                            .get()
                            .uri(memberUrl + "/internal/v1/" + kind + "/{id}", member)
                            .headers(h -> h.setBearerAuth(token()))
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<String>>() {
                            });
            if (ids == null || ids.size() > 500) throw new IllegalStateException();
            for (String id : ids) UUID.fromString(id);
            return ids;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Current relationships unavailable; retry the complete feed request");
        }
    }

    public record Decision(String memberId, boolean visible, boolean connected) {
    }
}
