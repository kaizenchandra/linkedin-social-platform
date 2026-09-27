package dev.network.media;

import static org.assertj.core.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest
class OracleMediaIT {
  static OracleContainer oracle;
  static GenericContainer<?> s3;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    if (System.getenv("TEST_DB_URL") == null) {
      oracle = new OracleContainer("gvenzl/oracle-free:23.9-slim-faststart");
      oracle.start();
      r.add("spring.datasource.url", oracle::getJdbcUrl);
      r.add("spring.datasource.username", oracle::getUsername);
      r.add("spring.datasource.password", oracle::getPassword);
      s3 =
          new GenericContainer<>("chrislusf/seaweedfs:4.47")
              .withCommand("mini", "-dir=/data")
              .withEnv("AWS_ACCESS_KEY_ID", "testkey")
              .withEnv("AWS_SECRET_ACCESS_KEY", "testsecret")
              .withEnv("S3_BUCKET", "network-media")
              .withExposedPorts(8333)
              .waitingFor(
                  org.testcontainers.containers.wait.strategy.Wait.forHttp("/").forStatusCode(403));
      s3.start();
      r.add("S3_ENDPOINT", () -> "http://" + s3.getHost() + ":" + s3.getMappedPort(8333));
      r.add("S3_ACCESS_KEY", () -> "testkey");
      r.add("S3_SECRET_KEY", () -> "testsecret");
    } else {
      r.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
      r.add("spring.datasource.username", () -> System.getenv("TEST_MEDIA_DB_USER"));
      r.add("spring.datasource.password", () -> System.getenv("TEST_MEDIA_DB_PASSWORD"));
    }
  }

  @Autowired MediaService media;
  @Autowired MediaRepository repo;
  @Autowired ObjectStore store;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  dev.network.web.ServiceHttp http;

  private byte[] png() throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  @Test
  void privateUploadAndAbandonedCleanupUseRealOracleAndS3() throws Exception {
    String actor = UUID.randomUUID().toString();
    var uploaded = media.upload(actor, new ByteArrayInputStream(png()));
    assertThat(uploaded.state()).isEqualTo("READY");
    assertThatThrownBy(() -> media.authorize(actor, uploaded.id()))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    var row = repo.findById(uploaded.id()).orElseThrow();
    try (var bytes = store.get(row.objectKey)) {
      assertThat(bytes.readAllBytes()).isNotEmpty();
    }
    row.updatedAt = Instant.now().minusSeconds(1000);
    repo.saveAndFlush(row);
    media.reconcile(row.id, Instant.now().minusSeconds(600));
    assertThat(repo.existsById(row.id)).isFalse();
    assertThatThrownBy(() -> store.get(row.objectKey))
        .isInstanceOf(software.amazon.awssdk.services.s3.model.NoSuchKeyException.class);
  }

  @Test
  void claimsReconcileAndDependencyFailureDoesNotDelete() throws Exception {
    String actor = UUID.randomUUID().toString();
    var uploaded = media.upload(actor, new ByteArrayInputStream(png()));
    var row = repo.findById(uploaded.id()).orElseThrow();
    row.state = "CLAIMED";
    row.resourceType = "POST";
    row.resourceId = UUID.randomUUID().toString();
    row.operationId = UUID.randomUUID().toString();
    row.operationMediaIds = row.id;
    row.updatedAt = Instant.now().minusSeconds(1000);
    repo.saveAndFlush(row);
    org.mockito.Mockito.when(
            http.post(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(MediaService.OwnerState.class)))
        .thenThrow(
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
    assertThatThrownBy(() -> media.reconcile(row.id, Instant.now().minusSeconds(600)))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThat(repo.findById(row.id).orElseThrow().state).isEqualTo("CLAIMED");
    org.mockito.Mockito.when(
            http.post(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(MediaService.OwnerState.class)))
        .thenReturn(new MediaService.OwnerState("COMMITTED", List.of(row.id)));
    media.reconcile(row.id, Instant.now().minusSeconds(600));
    assertThat(repo.findById(row.id).orElseThrow().state).isEqualTo("ATTACHED");
    try (var bytes = store.get(row.objectKey)) {
      assertThat(bytes.readAllBytes()).isNotEmpty();
    }
  }
}
