package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.Mission;
import io.mateu.space.domain.Picture;
import io.mateu.space.domain.Spacecraft;
import io.mateu.space.domain.SpacecraftComponent;
import io.mateu.space.infra.in.rest.dto.ComponentDto;
import io.mateu.space.infra.in.rest.dto.SpacecraftDto;
import io.mateu.space.infra.in.rest.query.FilterKind;
import io.mateu.space.infra.in.rest.query.PageResponse;
import io.mateu.space.infra.in.rest.query.QuerySpec;
import io.mateu.space.infra.out.SpaceRepository;
import io.mateu.space.infra.out.SpacecraftRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/spacecraft")
@Tag(name = "Spacecraft", description = "Capsules, orbiters, rovers, telescopes — and what they are made of")
public class SpacecraftController extends CrudController<Spacecraft, SpacecraftDto> {

  static final List<String> TYPES =
      List.of("CAPSULE", "LANDER", "ORBITER", "ROVER", "PROBE", "TELESCOPE", "STATION", "HELICOPTER");
  static final List<String> POWER = List.of("SOLAR", "RTG", "FUEL_CELL", "BATTERY");

  private final SpacecraftRepository spacecraft;

  public SpacecraftController(SpacecraftRepository spacecraft) {
    this.spacecraft = spacecraft;
  }

  @Override
  protected String collection() {
    return "spacecraft";
  }

  @Override
  protected SpaceRepository<Spacecraft> repository() {
    return spacecraft;
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("name", FilterKind.TEXT)
        .filter("type", FilterKind.EXACT)
        .filter("manufacturer", FilterKind.TEXT)
        .filter("powerSource", FilterKind.EXACT)
        .filter("massKg", FilterKind.NUMBER)
        .filter("powerWatts", FilterKind.NUMBER)
        .searchable("name", "manufacturer", "description")
        .defaultSort("name")
        .build();
  }

  @Operation(
      summary = "Every spacecraft that has a breakdown, as a tree",
      description = "spacecraft → subsystem → instrument, each node's `children` holding the next "
          + "level — the shape a tree grid reads. Not paged: the envelope holds every root.")
  @Transactional(readOnly = true)
  @GetMapping("/tree")
  public PageResponse<ComponentDto> tree(@RequestParam(required = false) String search) {
    String needle = search == null ? "" : search.trim().toLowerCase();
    List<ComponentDto> roots = spacecraft.findAll(Sort.by("name")).stream()
        .filter(s -> !s.getComponents().isEmpty())
        .map(s -> new ComponentDto(-s.getId(), s.getName(), "SPACECRAFT", s.getType(), s.getId(),
            s.getName(), null, null, urlOf(s.getId()), nodes(s, null)))
        .filter(n -> needle.isEmpty() || matches(n, needle))
        .toList();
    return PageResponse.all(roots);
  }

  @Operation(summary = "One spacecraft's components, as a tree")
  @Transactional(readOnly = true)
  @GetMapping("/{id}/components/tree")
  public PageResponse<ComponentDto> componentTree(@PathVariable Long id) {
    Spacecraft s = spacecraft.findById(id).orElseThrow(() -> new NotFoundException(collection(), id));
    return PageResponse.all(nodes(s, null));
  }

  private static boolean matches(ComponentDto node, String needle) {
    return node.name().toLowerCase().contains(needle)
        || (node.children() != null && node.children().stream().anyMatch(c -> matches(c, needle)));
  }

  static List<ComponentDto> nodes(Spacecraft s, SpacecraftComponent parent) {
    return s.getComponents().stream()
        .filter(c -> c.getParent() == parent)
        .map(c -> ComponentsController.dto(c, nodes(s, c)))
        .toList();
  }

  @Override
  protected Spacecraft newEntity() {
    return new Spacecraft();
  }

  @Override
  protected Long idOf(SpacecraftDto dto) {
    return dto.id();
  }

  @Override
  protected SpacecraftDto toDto(Spacecraft s) {
    Picture p = s.getPicture() == null ? new Picture() : s.getPicture();
    return new SpacecraftDto(
        s.getId(),
        s.getName(),
        s.getType(),
        s.getManufacturer(),
        s.getMassKg(),
        s.getPowerWatts(),
        s.getPowerSource(),
        s.getDescription(),
        s.getComponents().size(),
        s.getMissions().stream().map(Mission::getId).toList(),
        s.getWikidataId(),
        wikidataUrl(s.getWikidataId()),
        urlOf(s.getId()),
        p.getImage(),
        p.getImageLarge(),
        p.getThumbnail(),
        p.getImageTitle(),
        p.getImageCredit(),
        p.getSourceUrl());
  }

  @Override
  protected void apply(SpacecraftDto dto, Spacecraft s) {
    s.setName(dto.name());
    s.setType(oneOf(dto.type(), "type", TYPES));
    s.setManufacturer(dto.manufacturer());
    s.setMassKg(dto.massKg());
    s.setPowerWatts(dto.powerWatts());
    s.setPowerSource(oneOf(dto.powerSource(), "powerSource", POWER));
    s.setDescription(dto.description());
    s.setWikidataId(keep(dto.wikidataId(), s.getWikidataId()));
    s.setPicture(Pictures.merge(s.getPicture(), dto.image(), dto.imageLarge(), dto.thumbnail(),
        dto.imageTitle(), dto.imageCredit(), dto.sourceUrl()));
  }
}
