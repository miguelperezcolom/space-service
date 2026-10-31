package io.mateu.space.infra.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A window between two dates, as a date-range widget holds it: {@code {"from": "2027-01-01", "to":
 * "2027-02-15"}}. Answered as that object; READ leniently — the object, or any text carrying two
 * ISO dates (the object as server-side interpolation prints it, {@code "{from=2027-01-01,
 * to=2027-02-15}"}, or {@code "2027-01-01;2027-02-15"}). One date is a window that starts then.
 */
public record DateWindow(LocalDate from, LocalDate to) {

  private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static DateWindow parse(Object raw) {
    if (raw == null) return null;
    if (raw instanceof Map<?, ?> map) {
      return of(text(map.get("from")), text(map.get("to")));
    }
    List<String> dates = new ArrayList<>();
    Matcher m = DATE.matcher(String.valueOf(raw));
    while (m.find()) dates.add(m.group());
    return dates.isEmpty() ? null : of(dates.get(0), dates.size() > 1 ? dates.get(1) : null);
  }

  private static DateWindow of(String from, String to) {
    LocalDate f = from == null ? null : LocalDate.parse(from.substring(0, 10));
    LocalDate t = to == null ? null : LocalDate.parse(to.substring(0, 10));
    return f == null && t == null ? null : new DateWindow(f, t);
  }

  private static String text(Object o) {
    return o == null || String.valueOf(o).isBlank() ? null : String.valueOf(o);
  }
}
