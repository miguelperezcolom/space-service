package io.mateu.space.infra.in.rest.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What one collection lets a caller filter and search by. It is the API's contract in one object:
 * the controller declares it, {@link SpecificationBuilder} enforces it, and anything a caller sends
 * that is not in here is rejected rather than quietly ignored — a silently dropped filter returns a
 * page of data that looks right and is not.
 *
 * @param filters query-parameter name → the entity property path it compares, and how
 * @param searchPaths the text properties {@code ?search=} scans, in the order they are tried
 * @param defaultSort the property a page comes back ordered by when the caller asks for no order,
 *     so paging is stable (an unordered page 2 may repeat rows from page 1)
 */
public record QuerySpec(Map<String, Filter> filters, List<String> searchPaths, String defaultSort) {

  /** One filterable field: where it lives on the entity, and how it compares. */
  public record Filter(String path, FilterKind kind) {}

  public static Builder builder() {
    return new Builder();
  }

  public boolean knows(String parameter) {
    return filters.containsKey(parameter);
  }

  public static final class Builder {
    private final Map<String, Filter> filters = new LinkedHashMap<>();
    private List<String> searchPaths = List.of();
    private String defaultSort = "id";

    /** Declares a filter whose query-parameter name is also its property path — the usual case. */
    public Builder filter(String name, FilterKind kind) {
      filters.put(name, new Filter(name, kind));
      return this;
    }

    /** Declares a filter reaching a property under another name, e.g. destinationId → destination.id. */
    public Builder filter(String name, String path, FilterKind kind) {
      filters.put(name, new Filter(path, kind));
      return this;
    }

    public Builder searchable(String... paths) {
      this.searchPaths = List.of(paths);
      return this;
    }

    public Builder defaultSort(String property) {
      this.defaultSort = property;
      return this;
    }

    public QuerySpec build() {
      return new QuerySpec(Map.copyOf(filters), searchPaths, defaultSort);
    }
  }
}
