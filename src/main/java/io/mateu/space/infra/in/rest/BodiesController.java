package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.CelestialBody;
import io.mateu.space.domain.Picture;
import io.mateu.space.infra.in.rest.dto.CelestialBodyDto;
import io.mateu.space.infra.in.rest.query.FilterKind;
import io.mateu.space.infra.in.rest.query.PageResponse;
import io.mateu.space.infra.in.rest.query.QuerySpec;
import io.mateu.space.infra.out.CelestialBodyRepository;
import io.mateu.space.infra.out.MissionRepository;
import io.mateu.space.infra.out.SpaceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bodies")
@Tag(name = "Celestial bodies", description = "The Sun, planets, dwarf planets, moons and asteroids")
public class BodiesController extends CrudController<CelestialBody, CelestialBodyDto> {

  static final List<String> TYPES =
      List.of("STAR", "PLANET", "DWARF_PLANET", "MOON", "ASTEROID", "KUIPER_BELT_OBJECT", "COMET");

  private final CelestialBodyRepository bodies;
  private final MissionRepository missions;

  public BodiesController(CelestialBodyRepository bodies, MissionRepository missions) {
    this.bodies = bodies;
    this.missions = missions;
  }

  @Override
  protected String collection() {
    return "bodies";
  }

  @Override
  protected SpaceRepository<CelestialBody> repository() {
    return bodies;
  }

  @Override
  protected java.util.Map<String, String> sortAliases() {
    return java.util.Map.of("parentName", "parent.name");
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("name", FilterKind.TEXT)
        .filter("type", FilterKind.EXACT)
        .filter("parentId", "parent.id", FilterKind.REF)
        .filter("radiusKm", FilterKind.NUMBER)
        .filter("orbitalPeriodDays", FilterKind.NUMBER)
        .filter("surfaceGravity", FilterKind.NUMBER)
        .searchable("name", "description")
        .defaultSort("id")
        .build();
  }

  @Operation(
      summary = "The solar system, as a tree",
      description = "Every body with no parent at the root, each node's `children` holding what "
          + "orbits it — the Sun → Jupiter → Europa. Not paged. With `search`, the branches that "
          + "lead to a match.")
  @Transactional(readOnly = true)
  @GetMapping("/tree")
  public PageResponse<CelestialBodyDto> tree(@RequestParam(required = false) String search) {
    String needle = search == null ? "" : search.trim().toLowerCase();
    Map<Long, Long> counts = missionCounts();
    List<CelestialBodyDto> roots = bodies.findAll(Sort.by("id")).stream()
        .filter(b -> b.getParent() == null)
        .map(b -> node(b, counts, needle))
        .filter(n -> n != null)
        .toList();
    return PageResponse.all(roots);
  }

  private CelestialBodyDto node(CelestialBody b, Map<Long, Long> counts, String needle) {
    List<CelestialBodyDto> children = b.getChildren().stream()
        .map(c -> node(c, counts, needle))
        .filter(c -> c != null)
        .toList();
    boolean hit = needle.isEmpty() || b.getName().toLowerCase().contains(needle);
    if (!hit && children.isEmpty()) return null;
    return dto(b, counts.getOrDefault(b.getId(), 0L).intValue(), children);
  }

  private Map<Long, Long> missionCounts() {
    return missions.findAll().stream()
        .filter(m -> m.getDestination() != null)
        .collect(Collectors.groupingBy(m -> m.getDestination().getId(), Collectors.counting()));
  }

  @Override
  protected CelestialBody newEntity() {
    return new CelestialBody();
  }

  @Override
  protected Long idOf(CelestialBodyDto dto) {
    return dto.id();
  }

  @Override
  protected CelestialBodyDto toDto(CelestialBody b) {
    return dto(b, (int) missions.count((root, q, cb) -> cb.equal(root.get("destination").get("id"), b.getId())), null);
  }

  private CelestialBodyDto dto(CelestialBody b, Integer missionCount, List<CelestialBodyDto> children) {
    Picture p = b.getPicture() == null ? new Picture() : b.getPicture();
    return new CelestialBodyDto(
        b.getId(),
        b.getName(),
        b.getType(),
        b.getParent() == null ? null : b.getParent().getId(),
        b.getParent() == null ? null : b.getParent().getName(),
        b.getRadiusKm(),
        b.getMassKg(),
        b.getOrbitalPeriodDays(),
        b.getSurfaceGravity(),
        b.getDescription(),
        missionCount,
        b.getWikidataId(),
        wikidataUrl(b.getWikidataId()),
        urlOf(b.getId()),
        p.getImage(),
        p.getImageLarge(),
        p.getThumbnail(),
        p.getImageTitle(),
        p.getImageCredit(),
        p.getSourceUrl(),
        children);
  }

  @Override
  protected void apply(CelestialBodyDto dto, CelestialBody b) {
    b.setName(dto.name());
    b.setType(oneOf(dto.type(), "type", TYPES));
    CelestialBody parent = resolveOne(dto.parentId(), bodies, "celestial body");
    for (var p = parent; p != null; p = p.getParent()) {
      if (p == b) throw new InvalidValueException("parentId", String.valueOf(dto.parentId()), List.of("a body that does not orbit this one"));
    }
    b.setParent(parent);
    b.setRadiusKm(dto.radiusKm());
    b.setMassKg(dto.massKg());
    b.setOrbitalPeriodDays(dto.orbitalPeriodDays());
    b.setSurfaceGravity(dto.surfaceGravity());
    b.setDescription(dto.description());
    b.setWikidataId(keep(dto.wikidataId(), b.getWikidataId()));
    b.setPicture(Pictures.merge(b.getPicture(), dto.image(), dto.imageLarge(), dto.thumbnail(),
        dto.imageTitle(), dto.imageCredit(), dto.sourceUrl()));
  }

}
