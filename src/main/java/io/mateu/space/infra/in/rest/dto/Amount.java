package io.mateu.space.infra.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.math.BigDecimal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An amount of money as a money widget holds it: {@code {"value": 1.16E9, "currency": "USD"}}.
 *
 * <p>Answered as that object. READ leniently, because a declarative client may hand it over in
 * several shapes: the object itself, a bare number ({@code 1160000000}), a numeric string, or the
 * object as its server-side template interpolation prints it ({@code "{value=1.16E9, currency=USD,
 * locale=en-US}"}). Blank is no amount. The currency defaults to USD, which is what the API stores.
 */
public record Amount(BigDecimal value, String currency, String locale) {

  private static final Pattern VALUE = Pattern.compile("value\\s*[=:]\\s*\"?(-?[0-9.]+(?:[eE][-+]?[0-9]+)?)");
  private static final Pattern CURRENCY = Pattern.compile("currency\\s*[=:]\\s*\"?([A-Za-z]{3})");

  public static Amount usd(BigDecimal value) {
    return value == null ? null : new Amount(value, "USD", "en-US");
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static Amount parse(Object raw) {
    if (raw == null) return null;
    if (raw instanceof Number n) return usd(new BigDecimal(n.toString()));
    if (raw instanceof Map<?, ?> map) {
      Object v = map.get("value");
      if (v == null || String.valueOf(v).isBlank()) return null;
      Object c = map.get("currency");
      return new Amount(new BigDecimal(String.valueOf(v)), c == null ? "USD" : String.valueOf(c), "en-US");
    }
    String s = String.valueOf(raw).trim();
    if (s.isEmpty() || "null".equals(s)) return null;
    if (s.matches("-?[0-9.]+(?:[eE][-+]?[0-9]+)?")) return usd(new BigDecimal(s));
    Matcher v = VALUE.matcher(s);
    if (!v.find()) throw new IllegalArgumentException("Not an amount: " + s);
    Matcher c = CURRENCY.matcher(s);
    return new Amount(new BigDecimal(v.group(1)), c.find() ? c.group(1).toUpperCase() : "USD", "en-US");
  }
}
