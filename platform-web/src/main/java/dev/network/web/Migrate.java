package dev.network.web;

import java.sql.*;
import org.flywaydb.core.Flyway;

/** Dedicated deployment entry point; no HTTP server, JPA, scheduler or Kafka consumer starts. */
public final class Migrate {
  private Migrate() {}

  public static void main(String[] args) throws Exception {
    String url = required("DB_URL"),
        user = required("DB_USER"),
        password = required("DB_PASSWORD"),
        runtime = required("DB_RUNTIME_USER");
    if (!runtime.matches("[a-z][a-z0-9_]{1,29}"))
      throw new IllegalArgumentException("Invalid runtime principal");
    Flyway.configure()
        .dataSource(url, user, password)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    try (var connection = DriverManager.getConnection(url, user, password);
        var query = connection.createStatement();
        var rows =
            query.executeQuery(
                "SELECT table_name FROM user_tables WHERE table_name NOT LIKE 'flyway%'")) {
      while (rows.next()) {
        String table = rows.getString(1);
        if (!table.matches("[A-Z][A-Z0-9_]*"))
          throw new IllegalArgumentException("Unexpected table name");
        try (var grant = connection.createStatement()) {
          grant.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON " + table + " TO " + runtime);
        }
      }
    }
    System.out.println("Migrations complete; runtime DML grants applied.");
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
    return value;
  }
}
