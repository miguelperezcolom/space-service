package io.mateu.space.infra.in.rest;

import io.mateu.space.infra.in.rest.query.PageResponse;
import io.mateu.space.infra.in.rest.query.QuerySpec;
import io.mateu.space.infra.in.rest.query.SpecificationBuilder;
import io.mateu.space.infra.out.SpaceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

/**
 * The collections differ in their fields and their references, not in how they behave, so the
 * behaviour lives here once: list with search, filters, sort and paging; read; create; replace;
 * delete. A subclass declares what it is made of and nothing about HTTP.
 *
 * @param <E> the entity
 * @param <D> its wire shape
 */
public abstract class CrudController<E, D> {

  /**
   * The most rows a collection may hold. The database is in the heap, so this is what keeps a flood
   * of creates from becoming an OutOfMemoryError. Well above the seed (138 components at most).
   */
  @Value("${space.max-rows-per-collection:2000}")
  private long maxRows;

  /** The collection's path segment — {@code missions} — used to build each row's self link. */
  protected abstract String collection();

  protected abstract SpaceRepository<E> repository();

  /** What this collection may be searched and filtered by. */
  protected abstract QuerySpec querySpec();

  protected abstract D toDto(E entity);

  /** Creates a blank entity for a POST. */
  protected abstract E newEntity();

  /**
   * Copies the writable half of a DTO onto an entity, resolving referenced ids into rows. The same
   * method serves create and replace, so the two can never drift apart.
   */
  protected abstract void apply(D dto, E entity);

  protected String urlOf(Long id) {
    return "/api/" + collection() + "/" + id;
  }

  @Operation(
      summary = "List, filtered and paged",
      description =
          "Every condition is evaluated by the database. `search` scans this collection's text "
              + "fields; each declared filter is an exact or containment match depending on the "
              + "field; a numeric or date filter also accepts `<field>_from` and `<field>_to`, "
              + "both inclusive; repeating a parameter means OR. An undeclared filter is a 400, "
              + "never a silently unfiltered page.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "A page of rows, with the total"),
    @ApiResponse(responseCode = "400", description = "An undeclared filter or an unparseable value")
  })
  // The session boundary is the endpoint, not the whole request: `open-in-view` is off (it hides an
  // N+1 behind a view render), so the mapping to DTOs — which touches the lazy references — has to
  // happen inside a transaction that is opened here on purpose.
  @Transactional(readOnly = true)
  @GetMapping
  public PageResponse<D> list(
      @Parameter(in = ParameterIn.QUERY, description = "Free text over this collection's text fields")
          @RequestParam(required = false)
          String search,
      @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
      @Parameter(description = "Rows per page (max 200)") @RequestParam(defaultValue = "20") int size,
      @Parameter(description = "`field` or `field,desc`") @RequestParam(required = false) String sort,
      @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> allParams) {

    QuerySpec spec = querySpec();
    Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), sortOf(sort, spec));
    Page<E> found = repository().findAll(SpecificationBuilder.build(allParams, spec), pageable);
    return PageResponse.of(found.map(this::toDto));
  }

  @Operation(summary = "One row by id")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The row"),
    @ApiResponse(responseCode = "404", description = "No row with that id")
  })
  @Transactional(readOnly = true)
  @GetMapping("/{id}")
  public D get(@PathVariable Long id) {
    return toDto(repository().findById(id).orElseThrow(() -> new NotFoundException(collection(), id)));
  }

  @Operation(summary = "Create", description = "Requires the write API key.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Created; the Location header carries the row"),
    @ApiResponse(responseCode = "400", description = "A referenced id does not exist, or a field is invalid"),
    @ApiResponse(responseCode = "401", description = "Missing or wrong write key"),
    @ApiResponse(responseCode = "507", description = "The collection is at its row ceiling")
  })
  @Transactional
  @PostMapping
  public ResponseEntity<D> create(@Valid @RequestBody D dto) {
    if (repository().count() >= maxRows) throw new CollectionFullException(collection(), maxRows);
    E entity = newEntity();
    apply(dto, entity);
    onCreate(entity);
    E saved = repository().save(entity);
    D body = toDto(saved);
    return ResponseEntity.created(URI.create(urlOf(idOf(body)))).body(body);
  }

  @Operation(summary = "Replace", description = "Requires the write API key.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The updated row"),
    @ApiResponse(responseCode = "400", description = "A referenced id does not exist, or a field is invalid"),
    @ApiResponse(responseCode = "401", description = "Missing or wrong write key"),
    @ApiResponse(responseCode = "404", description = "No row with that id")
  })
  @Transactional
  @PutMapping("/{id}")
  public D update(@PathVariable Long id, @Valid @RequestBody D dto) {
    E entity = repository().findById(id).orElseThrow(() -> new NotFoundException(collection(), id));
    apply(dto, entity);
    return toDto(repository().save(entity));
  }

  @Operation(summary = "Delete", description = "Requires the write API key.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Deleted"),
    @ApiResponse(responseCode = "401", description = "Missing or wrong write key"),
    @ApiResponse(responseCode = "404", description = "No row with that id")
  })
  @Transactional
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    if (!repository().existsById(id)) throw new NotFoundException(collection(), id);
    repository().deleteById(id);
    // flushed here, inside the repository's exception translation, so a row still referenced
    // elsewhere is a 409 and not an opaque failure at commit
    repository().flush();
    return ResponseEntity.noContent().build();
  }

  /**
   * An unordered page is not stable: the database may hand back page 2 with rows already seen on
   * page 1. So a request that names no order still gets one.
   */
  private Sort sortOf(String sort, QuerySpec spec) {
    if (sort == null || sort.isBlank()) return Sort.by(spec.defaultSort());
    String[] parts = sort.split(",");
    Sort.Direction direction =
        parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")
            ? Sort.Direction.DESC
            : Sort.Direction.ASC;
    String field = parts[0].trim();
    if (field.isEmpty()) return Sort.by(spec.defaultSort());
    return Sort.by(direction, sortAliases().getOrDefault(field, field)).and(Sort.by("id"));
  }

  /**
   * The wire names a client sorts by that are not entity properties: a table sorts by the column it
   * shows ({@code destinationName}), which lives elsewhere on the entity ({@code destination.name}).
   */
  protected java.util.Map<String, String> sortAliases() {
    return java.util.Map.of();
  }

  /** The DTOs are records whose first component is the id; this reads it without reflection games. */
  protected abstract Long idOf(D dto);

  /** Resolves a list of referenced ids into rows, failing on the first one that does not exist. */
  protected <R> List<R> resolve(List<Long> ids, SpaceRepository<R> repository, String what) {
    if (ids == null) return List.of();
    return ids.stream()
        .map(id -> repository.findById(id).orElseThrow(() -> new BadReferenceException(what, id)))
        .toList();
  }

  /**
   * A closed-set value (a status, a type): blank is "none", anything outside the set is a 400 naming
   * what is accepted. Matched without regard to case, stored as declared.
   */
  protected static String oneOf(String value, String field, java.util.Collection<String> allowed) {
    if (value == null || value.isBlank()) return null;
    return allowed.stream()
        .filter(a -> a.equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElseThrow(() -> new InvalidValueException(field, value, allowed));
  }

  /** What a new row gets that the body did not say — a planned mission, a task in the backlog. */
  protected void onCreate(E entity) {}

  /** A link to the record's Wikidata item, or null. */
  protected static String wikidataUrl(String qid) {
    return qid == null || qid.isBlank() ? null : "https://www.wikidata.org/wiki/" + qid;
  }

  /** Keeps {@code current} when the body leaves the value out — for media and to-many references. */
  protected static <T> T keep(T given, T current) {
    return given == null ? current : given;
  }

  /** Resolves a single optional reference. A null id means "no reference", not an error. */
  protected <R> R resolveOne(Long id, SpaceRepository<R> repository, String what) {
    if (id == null) return null;
    return repository.findById(id).orElseThrow(() -> new BadReferenceException(what, id));
  }
}
