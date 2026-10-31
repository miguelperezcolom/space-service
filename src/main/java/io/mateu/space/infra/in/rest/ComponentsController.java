package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.SpacecraftComponent;
import io.mateu.space.infra.in.rest.dto.ComponentDto;
import io.mateu.space.infra.in.rest.query.FilterKind;
import io.mateu.space.infra.in.rest.query.QuerySpec;
import io.mateu.space.infra.out.SpaceRepository;
import io.mateu.space.infra.out.SpacecraftComponentRepository;
import io.mateu.space.infra.out.SpacecraftRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The nodes of the spacecraft breakdowns, flat. A node names its spacecraft and its parent; a parent
 * must belong to the same spacecraft, and a node cannot be its own ancestor.
 */
@RestController
@RequestMapping("/api/components")
@Tag(name = "Components", description = "Subsystems and instruments of the spacecraft")
public class ComponentsController extends CrudController<SpacecraftComponent, ComponentDto> {

  static final List<String> KINDS = List.of("SUBSYSTEM", "INSTRUMENT", "COMPONENT");

  private final SpacecraftComponentRepository components;
  private final SpacecraftRepository spacecraft;

  public ComponentsController(
      SpacecraftComponentRepository components, SpacecraftRepository spacecraft) {
    this.components = components;
    this.spacecraft = spacecraft;
  }

  @Override
  protected String collection() {
    return "components";
  }

  @Override
  protected SpaceRepository<SpacecraftComponent> repository() {
    return components;
  }

  @Override
  protected java.util.Map<String, String> sortAliases() {
    return java.util.Map.of("spacecraftName", "spacecraft.name", "parentName", "parent.name");
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("name", FilterKind.TEXT)
        .filter("kind", FilterKind.EXACT)
        .filter("spacecraftId", "spacecraft.id", FilterKind.REF)
        .filter("parentId", "parent.id", FilterKind.REF)
        .searchable("name", "description")
        .defaultSort("id")
        .build();
  }

  @Override
  protected SpacecraftComponent newEntity() {
    return new SpacecraftComponent();
  }

  @Override
  protected Long idOf(ComponentDto dto) {
    return dto.id();
  }

  @Override
  protected ComponentDto toDto(SpacecraftComponent c) {
    return dto(c, null);
  }

  static ComponentDto dto(SpacecraftComponent c, List<ComponentDto> children) {
    return new ComponentDto(
        c.getId(),
        c.getName(),
        c.getKind(),
        c.getDescription(),
        c.getSpacecraft() == null ? null : c.getSpacecraft().getId(),
        c.getSpacecraft() == null ? null : c.getSpacecraft().getName(),
        c.getParent() == null ? null : c.getParent().getId(),
        c.getParent() == null ? null : c.getParent().getName(),
        "/api/components/" + c.getId(),
        children);
  }

  @Override
  protected void apply(ComponentDto dto, SpacecraftComponent c) {
    c.setName(dto.name());
    c.setKind(oneOf(dto.kind(), "kind", KINDS));
    c.setDescription(dto.description());
    var owner = resolveOne(dto.spacecraftId(), spacecraft, "spacecraft");
    if (owner == null) throw new InvalidValueException("spacecraftId", "null", List.of("a spacecraft id"));
    c.setSpacecraft(owner);
    var parent = resolveOne(dto.parentId(), components, "component");
    for (var p = parent; p != null; p = p.getParent()) {
      if (p == c) throw new InvalidValueException("parentId", String.valueOf(dto.parentId()), List.of("a node that is not its own descendant"));
    }
    if (parent != null && parent.getSpacecraft() != owner) {
      throw new InvalidValueException("parentId", String.valueOf(dto.parentId()), List.of("a node of the same spacecraft"));
    }
    c.setParent(parent);
  }
}
