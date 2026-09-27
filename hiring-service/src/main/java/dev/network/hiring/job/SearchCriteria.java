package dev.network.hiring.job;

import java.util.*;

/**
 * Shared matching semantics inside hiring only: literal AND keywords and literal location
 * substring.
 */
public record SearchCriteria(
    String keywords,
    List<String> companyIds,
    String location,
    Job.Work workArrangement,
    Job.Employment employmentType) {
  public SearchCriteria {
    keywords = normalize(keywords, 100);
    location = normalize(location, 150);
    if (!keywords.isEmpty() && keywords.split("\\s+").length > 8)
      throw new IllegalArgumentException("At most8 search terms");
    companyIds =
        companyIds == null
            ? List.of()
            : companyIds.stream().peek(UUID::fromString).distinct().sorted().toList();
    if (companyIds.size() > 20) throw new IllegalArgumentException("At most20 companies");
  }

  private static String normalize(String s, int max) {
    if (s == null) return "";
    if (s.length() > max) throw new IllegalArgumentException("Search too long");
    return s.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  public static String term(String value) {
    return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }

  public String sql(String alias, List<Object> args) {
    String p = alias.isEmpty() ? "" : alias + ".";
    String where = "";
    if (!keywords.isEmpty())
      for (String term : keywords.split(" ")) {
        where +=
            " AND (LOWER("
                + p
                + "title) LIKE ? ESCAPE '!' OR LOWER("
                + p
                + "description) LIKE ? ESCAPE '!')";
        args.add(term(term));
        args.add(term(term));
      }
    if (!companyIds.isEmpty()) {
      where +=
          " AND "
              + p
              + "company_id IN ("
              + String.join(",", Collections.nCopies(companyIds.size(), "?"))
              + ")";
      args.addAll(companyIds);
    }
    if (!location.isEmpty()) {
      where += " AND LOWER(" + p + "location) LIKE ? ESCAPE '!'";
      args.add(term(location));
    }
    if (workArrangement != null) {
      where += " AND " + p + "work_arrangement=?";
      args.add(workArrangement.name());
    }
    if (employmentType != null) {
      where += " AND " + p + "employment_type=?";
      args.add(employmentType.name());
    }
    return where;
  }
}
