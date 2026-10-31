package io.mateu.space.infra.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A list of referenced ids, as a body sends it.
 *
 * <p>It is always ANSWERED as a JSON array, but it is READ leniently: an array ({@code [1, 2]}), a
 * comma-joined string ({@code "1,2"}) or the same in brackets ({@code "[1, 2]"}). A form that posts
 * its multi-select by interpolating it into a JSON template — which is what a declarative UI with no
 * code behind it does — produces the string forms, and refusing them would make the association
 * impossible to write from such a client. A blank string is an empty list.
 */
@ArraySchema(schema = @Schema(type = "integer", format = "int64"))
public record IdList(List<Long> ids) {

  public IdList {
    ids = ids == null ? List.of() : List.copyOf(ids);
  }

  public static IdList of(Collection<Long> ids) {
    return new IdList(new ArrayList<>(ids));
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static IdList parse(Object raw) {
    List<Long> out = new ArrayList<>();
    if (raw instanceof Collection<?> items) {
      for (Object item : items) add(out, item == null ? null : String.valueOf(item));
    } else if (raw != null) {
      for (String part : String.valueOf(raw).replace("[", "").replace("]", "").split(",")) {
        add(out, part);
      }
    }
    return new IdList(out);
  }

  private static void add(List<Long> out, String value) {
    if (value == null || value.isBlank() || "null".equals(value.trim())) return;
    String trimmed = value.trim();
    if (trimmed.endsWith(".0")) trimmed = trimmed.substring(0, trimmed.length() - 2);
    try {
      out.add(Long.valueOf(trimmed));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Not an id: " + value);
    }
  }

  @JsonValue
  @Override
  public List<Long> ids() {
    return ids;
  }
}
