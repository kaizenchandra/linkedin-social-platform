package dev.network.hiring;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.network.hiring.company.*;
import dev.network.web.ServiceHttp;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.oracle.OracleContainer;

@SpringBootTest(
    properties = {
      "network.outbox.enabled=false",
      "network.alerts.enabled=false",
      "spring.kafka.listener.auto-startup=false"
    })
abstract class OracleHiringTest {
  static OracleContainer oracle;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    if (System.getenv("TEST_DB_URL") == null) {
      oracle = new OracleContainer("gvenzl/oracle-free:23.9-slim-faststart");
      oracle.start();
      r.add("spring.datasource.url", oracle::getJdbcUrl);
      r.add("spring.datasource.username", oracle::getUsername);
      r.add("spring.datasource.password", oracle::getPassword);
    } else {
      r.add("spring.datasource.url", () -> System.getenv("TEST_DB_URL"));
      r.add("spring.datasource.username", () -> System.getenv("TEST_HIRING_DB_USER"));
      r.add("spring.datasource.password", () -> System.getenv("TEST_HIRING_DB_PASSWORD"));
    }
  }

  @Autowired CompanyService companies;
  @Autowired JdbcTemplate db;
  @MockitoBean ServiceHttp http;

  @BeforeEach
  void profiles() {
    when(http.post(anyString(), any(), eq(Boolean.class))).thenReturn(true);
  }

  String id() {
    return UUID.randomUUID().toString();
  }

  CompanyService.Input input(String slug) {
    return new CompanyService.Input(
        "Company", slug, "Description", "Software", "Remote", "https://example.com");
  }

  int status(Runnable action) {
    try {
      action.run();
      return 200;
    } catch (ResponseStatusException e) {
      return e.getStatusCode().value();
    }
  }
}
