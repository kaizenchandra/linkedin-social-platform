package dev.network.web;

import static org.assertj.core.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;

import java.net.*;
import java.time.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.*;

class JwtValidationTest {
    static RSAKey key;
    static HttpServer server;
    static JwtDecoder decoder;

    @BeforeAll
    static void setup() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("test").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/jwks",
                e -> {
                    byte[] bytes =
                            new JWKSet(key.toPublicJWK())
                                    .toString()
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    e.getResponseHeaders().set("Content-Type", "application/json");
                    e.sendResponseHeaders(200, bytes.length);
                    try (var out = e.getResponseBody()) {
                        out.write(bytes);
                    }
                });
        server.start();
        decoder =
                new SecurityConfiguration()
                        .decoder(
                                "https://issuer.test",
                                "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                                "network-api");
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    String token(String issuer, String audience, String subject, Instant expiry, RSAKey signer)
            throws Exception {
        var claims =
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .audience(audience)
                        .subject(subject)
                        .issueTime(Date.from(Instant.now().minusSeconds(300)))
                        .expirationTime(Date.from(expiry))
                        .build();
        var jwt =
                new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), claims);
        jwt.sign(new RSASSASigner(signer));
        return jwt.serialize();
    }

    @Test
    void validatesRealSignatureAndRequiredClaims() throws Exception {
        assertThat(
                decoder.decode(
                        token(
                                "https://issuer.test",
                                "network-api",
                                UUID.randomUUID().toString(),
                                Instant.now().plusSeconds(300),
                                key)))
                .isNotNull();
    }

    @Test
    void rejectsExpiredWrongIssuerWrongAudienceMissingSubjectAndForgedSignature() throws Exception {
        String id = UUID.randomUUID().toString();
        var now = Instant.now();
        for (String value :
                List.of(
                        token("https://issuer.test", "network-api", id, now.minusSeconds(120), key),
                        token("https://forged.test", "network-api", id, now.plusSeconds(300), key),
                        token("https://issuer.test", "wrong", id, now.plusSeconds(300), key),
                        token("https://issuer.test", "network-api", null, now.plusSeconds(300), key),
                        token(
                                "https://issuer.test",
                                "network-api",
                                id,
                                now.plusSeconds(300),
                                new RSAKeyGenerator(2048).generate())))
            assertThatThrownBy(() -> decoder.decode(value)).isInstanceOf(JwtException.class);
    }
}
