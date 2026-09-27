package dev.network.media;

import dev.network.web.ServiceHttp;
import io.micrometer.core.instrument.MeterRegistry;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.Semaphore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaService {
    private final MediaRepository repo;
    private final ObjectStore store;
    private final ImageValidator validator;
    private final TransactionTemplate tx;
    private final ServiceHttp http;
    private final Clock clock;
    private final String memberUrl, contentUrl, hiringUrl;
    private final MeterRegistry metrics;
    private final Semaphore uploads = new Semaphore(1);

    public MediaService(
            MediaRepository repo,
            ObjectStore store,
            ImageValidator validator,
            org.springframework.transaction.PlatformTransactionManager tm,
            ServiceHttp http,
            Clock clock,
            @Value("${MEMBER_URL:http://localhost:8081}") String memberUrl,
            @Value("${CONTENT_URL:http://localhost:8082}") String contentUrl,
            @Value("${HIRING_URL:http://localhost:8086}") String hiringUrl,
            MeterRegistry metrics) {
        this.repo = repo;
        this.store = store;
        this.validator = validator;
        tx = new TransactionTemplate(tm);
        this.http = http;
        this.clock = clock;
        this.memberUrl = memberUrl;
        this.contentUrl = contentUrl;
        this.hiringUrl = hiringUrl;
        this.metrics = metrics;
    }

    private Instant now() {
        return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found");
    }

    private String url(String type) {
        return switch (type) {
            case "PROFILE" -> memberUrl;
            case "POST" -> contentUrl;
            case "COMPANY" -> hiringUrl;
            default -> throw new IllegalArgumentException("Invalid resource type");
        };
    }

    private Map<String, Object> operation(String actor, Attachment a) {
        return Map.of(
                "actorId",
                actor,
                "operationId",
                a.operationId(),
                "resourceId",
                a.resourceId(),
                "mediaIds",
                a.mediaIds());
    }

    public View upload(String actor, InputStream input) throws IOException {
        if (!uploads.tryAcquire())
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Upload capacity busy; retry later");
        try {
            var m =
                    tx.execute(
                            s -> {
                                if (repo.countByOwnerIdAndCreatedAtAfter(actor, now().minusSeconds(3600)) >= 40)
                                    throw new ResponseStatusException(
                                            HttpStatus.TOO_MANY_REQUESTS, "Hourly upload limit reached");
                                var row = new MediaObject();
                                row.id = UUID.randomUUID().toString();
                                row.ownerId = actor;
                                row.objectKey = "images/" + UUID.randomUUID();
                                row.state = "TEMPORARY";
                                row.createdAt = now();
                                row.updatedAt = row.createdAt;
                                return repo.saveAndFlush(row);
                            });
            var image = validator.normalize(input);
            try {
                store.put(m.objectKey, image);
            } catch (Exception e) {
                metrics.counter("media.storage.failures").increment();
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "Object storage unavailable; upload not completed");
            }
            return tx.execute(
                    s -> {
                        var row = repo.lock(m.id).orElseThrow(this::missing);
                        row.state = "READY";
                        row.contentType = image.contentType();
                        row.byteSize = (long) image.bytes().length;
                        row.width = image.width();
                        row.height = image.height();
                        row.updatedAt = now();
                        return view(row);
                    });
        } finally {
            uploads.release();
        }
    }

    public OwnerState attach(String actor, Attachment a) {
        UUID.fromString(a.operationId());
        UUID.fromString(a.resourceId());
        if (a.mediaIds() == null
                || a.mediaIds().size() > (a.resourceType().equals("POST") ? 4 : 1)
                || new HashSet<>(a.mediaIds()).size() != a.mediaIds().size())
            throw new IllegalArgumentException("Invalid media list");
        a.mediaIds().forEach(UUID::fromString);
        String endpoint = url(a.resourceType()) + "/internal/v1/media/";
        var body = operation(actor, a);
        var prepared = http.post(endpoint + "prepare", body, OwnerState.class);
        if (prepared.state().equals("COMMITTED")) return prepared;
        try {
            tx.executeWithoutResult(
                    s -> {
                        for (String id : a.mediaIds().stream().sorted().toList()) {
                            var m = repo.lock(id).orElseThrow(this::missing);
                            if (!m.ownerId.equals(actor)) throw missing();
                            boolean sameResource =
                                    a.resourceId().equals(m.resourceId) && a.resourceType().equals(m.resourceType);
                            if (!(m.state.equals("READY")
                                    || (sameResource
                                    && (m.state.equals("ATTACHED")
                                    || (m.state.equals("CLAIMED")
                                    && a.operationId().equals(m.operationId))))))
                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT, "Media already claimed or unavailable");
                            m.state = "CLAIMED";
                            m.resourceType = a.resourceType();
                            m.resourceId = a.resourceId();
                            m.operationId = a.operationId();
                            m.operationMediaIds = String.join(",", a.mediaIds());
                            m.updatedAt = now();
                        }
                    });
        } catch (RuntimeException e) {
            try {
                http.post(endpoint + "resolve", body, OwnerState.class);
            } catch (Exception ignored) {
                metrics.counter("media.reconciliation.failures").increment();
            }
            throw e;
        }
        var committed = http.post(endpoint + "commit", body, OwnerState.class);
        tx.executeWithoutResult(
                s -> {
                    for (String id : a.mediaIds().stream().sorted().toList()) {
                        var m = repo.lock(id).orElseThrow(this::missing);
                        if (m.state.equals("CLAIMED") && a.operationId().equals(m.operationId)) {
                            m.state = "ATTACHED";
                            m.updatedAt = now();
                        }
                    }
                });
        return committed;
    }

    public MediaObject authorize(String actor, String id) {
        var m = repo.findById(id).orElseThrow(this::missing);
        if (!Set.of("ATTACHED", "CLAIMED").contains(m.state)) throw missing();
        Boolean allowed =
                http.post(
                        url(m.resourceType) + "/internal/v1/media/access",
                        Map.of("actorId", actor, "resourceId", m.resourceId, "mediaId", id),
                        Boolean.class);
        if (!Boolean.TRUE.equals(allowed)) throw missing();
        return m;
    }

    public void stream(MediaObject m, OutputStream output) throws IOException {
        try (var input = store.get(m.objectKey)) {
            input.transferTo(output);
        } catch (software.amazon.awssdk.core.exception.SdkException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Object storage unavailable");
        }
    }

    public View own(String actor, String id) {
        var m = repo.findById(id).orElseThrow(this::missing);
        if (!m.ownerId.equals(actor)) throw missing();
        return view(m);
    }

    public void reconcile(String id, Instant before) {
        tx.executeWithoutResult(
                s -> {
                    var m = repo.lock(id).orElse(null);
                    if (m == null || !m.updatedAt.isBefore(before)) return;
                    if (Set.of("CLAIMED", "ATTACHED").contains(m.state)) {
                        var ids =
                                m.operationMediaIds == null || m.operationMediaIds.isEmpty()
                                        ? List.<String>of()
                                        : List.of(m.operationMediaIds.split(","));
                        var a = new Attachment(m.operationId, m.resourceType, m.resourceId, ids);
                        // Owner resolve fences an expired PREPARED operation before allowing deletion.
                        var state =
                                http.post(
                                        url(m.resourceType) + "/internal/v1/media/resolve",
                                        operation(m.ownerId, a),
                                        OwnerState.class);
                        if (state.mediaIds().contains(m.id)) {
                            m.state = "ATTACHED";
                            m.updatedAt = now();
                            return;
                        }
                    }
                    m.state = "DELETE_PENDING";
                    try {
                        store.delete(m.objectKey);
                    } catch (Exception e) {
                        metrics.counter("media.storage.failures").increment();
                        throw new ResponseStatusException(
                                HttpStatus.SERVICE_UNAVAILABLE, "Storage cleanup deferred");
                    }
                    repo.delete(m);
                    metrics.counter("media.cleanup.deleted").increment();
                });
    }

    private View view(MediaObject m) {
        return new View(m.id, m.state, m.contentType, m.byteSize, m.width, m.height);
    }

    public record View(
            String id, String state, String contentType, Long bytes, Integer width, Integer height) {
    }

    public record Attachment(
            @jakarta.validation.constraints.NotBlank String operationId,
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.Pattern(regexp = "PROFILE|POST|COMPANY")
            String resourceType,
            @jakarta.validation.constraints.NotBlank String resourceId,
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max = 4)
            List<String> mediaIds) {
    }

    public record OwnerState(String state, List<String> mediaIds) {
    }
}
