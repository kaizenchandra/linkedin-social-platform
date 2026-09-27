package dev.network.web;

import java.util.List;

public final class Pages {
  private Pages() {}

  public static int size(int size) {
    if (size < 1 || size > 100) throw new IllegalArgumentException("size must be 1..100");
    return size;
  }

  public static int page(int page) {
    if (page < 0 || page > 10000) throw new IllegalArgumentException("page must be 0..10000");
    return page;
  }

  public record Slice<T>(List<T> items, String nextCursor) {}
}
