package dev.network.hiring.shared;

import dev.network.web.Pages;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

public final class HiringPages {
  public record Slice<T>(List<T> items, String nextCursor, boolean hasMore) {}

  public static String after(String cursor, List<Object> args, String column) {
    if (cursor == null) return "";
    if (cursor.length() > 200) throw new IllegalArgumentException("Invalid cursor");
    try {
      var parts =
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
              .split("\\|", -1);
      if (parts.length != 2) throw new IllegalArgumentException();
      var time = Timestamp.from(Instant.parse(parts[0]));
      UUID.fromString(parts[1]);
      args.add(time);
      args.add(time);
      args.add(parts[1]);
      return " AND (" + column + "<? OR (" + column + "=? AND id<?))";
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid cursor");
    }
  }

  public static String cursor(Instant at, String id) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((at + "|" + id).getBytes(StandardCharsets.UTF_8));
  }

  public static <T> Slice<T> slice(
      List<T> rows, int size, java.util.function.Function<T, String> cursor) {
    Pages.size(size);
    boolean more = rows.size() > size;
    var items = List.copyOf(rows.subList(0, Math.min(size, rows.size())));
    return new Slice<>(items, more ? cursor.apply(items.getLast()) : null, more);
  }
}
