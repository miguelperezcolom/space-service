package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.Astronaut;
import io.mateu.space.domain.Mission;
import io.mateu.space.domain.Picture;
import io.mateu.space.infra.in.rest.dto.Amount;
import io.mateu.space.infra.in.rest.dto.DateWindow;
import io.mateu.space.infra.in.rest.dto.IdList;
import io.mateu.space.infra.in.rest.dto.MissionDto;
import io.mateu.space.infra.in.rest.query.FilterKind;
import io.mateu.space.infra.in.rest.query.QuerySpec;
import io.mateu.space.infra.out.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Missions own their crew, so this is where a single write re-points who flew one. They also carry
 * the richest record: dates, references to a destination, a launch site and a spacecraft, money,
 * rich-text objectives and a photo gallery.
 */
@RestController
@RequestMapping("/api/missions")
@Tag(name = "Missions", description = "Crewed flights, probes, rovers and telescopes")
public class MissionsController extends CrudController<Mission, MissionDto> {

  static final List<String> TYPES = List.of("CREWED", "LUNAR_FLYBY", "FLYBY", "ORBITER", "LANDER",
      "ROVER", "HELICOPTER", "PROBE", "TELESCOPE", "SAMPLE_RETURN", "IMPACTOR");
  static final List<String> STATUSES =
      List.of("PLANNED", "ACTIVE", "COMPLETED", "PARTIAL_SUCCESS", "FAILED");

  private final MissionRepository missions;
  private final CelestialBodyRepository bodies;
  private final LaunchSiteRepository sites;
  private final SpacecraftRepository spacecraft;
  private final AstronautRepository astronauts;

  public MissionsController(
      MissionRepository missions,
      CelestialBodyRepository bodies,
      LaunchSiteRepository sites,
      SpacecraftRepository spacecraft,
      AstronautRepository astronauts) {
    this.missions = missions;
    this.bodies = bodies;
    this.sites = sites;
    this.spacecraft = spacecraft;
    this.astronauts = astronauts;
  }

  @Override
  protected String collection() {
    return "missions";
  }

  @Override
  protected SpaceRepository<Mission> repository() {
    return missions;
  }

  @Override
  protected java.util.Map<String, String> sortAliases() {
    return java.util.Map.of("destinationName", "destination.name", "launchSiteName", "launchSite.name", "spacecraftName", "spacecraft.name", "launchYear", "launchDate");
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("name", FilterKind.TEXT)
        .filter("program", FilterKind.EXACT)
        .filter("agency", FilterKind.TEXT)
        .filter("type", FilterKind.EXACT)
        .filter("status", FilterKind.EXACT)
        .filter("featured", FilterKind.BOOLEAN)
        .filter("launchDate", FilterKind.DATE)
        .filter("endDate", FilterKind.DATE)
        .filter("launchWindowStart", FilterKind.DATE)
        .filter("costUsd", FilterKind.NUMBER)
        .filter("destinationId", "destination.id", FilterKind.REF)
        .filter("launchSiteId", "launchSite.id", FilterKind.REF)
        .filter("spacecraftId", "spacecraft.id", FilterKind.REF)
        .filter("crewId", "crew.id", FilterKind.MEMBER)
        .searchable("name", "program", "agency", "summary")
        .defaultSort("launchDate")
        .build();
  }

  @Override
  protected Mission newEntity() {
    return new Mission();
  }

  @Override
  protected Long idOf(MissionDto dto) {
    return dto.id();
  }

  @Override
  protected MissionDto toDto(Mission m) {
    Picture p = m.getPicture() == null ? new Picture() : m.getPicture();
    return new MissionDto(
        m.getId(),
        m.getName(),
        m.getProgram(),
        m.getAgency(),
        m.getType(),
        m.getStatus(),
        m.getLaunchDate(),
        m.getEndDate(),
        m.getLaunchDate() == null ? null : m.getLaunchDate().getYear(),
        m.getLaunchWindowStart() == null && m.getLaunchWindowEnd() == null
            ? null
            : new DateWindow(m.getLaunchWindowStart(), m.getLaunchWindowEnd()),
        m.getDestination() == null ? null : m.getDestination().getId(),
        m.getDestination() == null ? null : m.getDestination().getName(),
        m.getLaunchSite() == null ? null : m.getLaunchSite().getId(),
        m.getLaunchSite() == null ? null : m.getLaunchSite().getName(),
        m.getSpacecraft() == null ? null : m.getSpacecraft().getId(),
        m.getSpacecraft() == null ? null : m.getSpacecraft().getName(),
        IdList.of(m.getCrew().stream().map(Astronaut::getId).toList()),
        m.getCrew().stream().map(Astronaut::getName).toList(),
        m.getCrew().size(),
        m.getCostUsd(),
        Amount.usd(m.getCostUsd()),
        m.getSummary(),
        m.getObjectives(),
        m.getFeatured(),
        m.getWikidataId(),
        wikidataUrl(m.getWikidataId()),
        urlOf(m.getId()),
        p.getImage(),
        p.getImageLarge(),
        p.getThumbnail(),
        p.getImageTitle(),
        p.getImageCredit(),
        p.getSourceUrl(),
        m.getGallery().stream().map(Pictures::dto).toList());
  }

  @Override
  protected void apply(MissionDto dto, Mission m) {
    m.setName(dto.name());
    m.setProgram(dto.program());
    m.setAgency(dto.agency());
    m.setType(oneOf(dto.type(), "type", TYPES));
    m.setStatus(oneOf(dto.status(), "status", STATUSES));
    m.setLaunchDate(dto.launchDate());
    m.setEndDate(dto.endDate());
    m.setDestination(resolveOne(dto.destinationId(), bodies, "celestial body"));
    m.setLaunchSite(resolveOne(dto.launchSiteId(), sites, "launch site"));
    m.setSpacecraft(resolveOne(dto.spacecraftId(), spacecraft, "spacecraft"));
    m.setCostUsd(dto.cost() != null ? dto.cost().value() : dto.costUsd());
    m.setLaunchWindowStart(dto.launchWindow() == null ? null : dto.launchWindow().from());
    m.setLaunchWindowEnd(dto.launchWindow() == null ? null : dto.launchWindow().to());
    m.setSummary(dto.summary());
    m.setObjectives(dto.objectives());
    m.setFeatured(dto.featured() != null && dto.featured());
    m.setWikidataId(keep(dto.wikidataId(), m.getWikidataId()));
    m.setPicture(Pictures.merge(m.getPicture(), dto.image(), dto.imageLarge(), dto.thumbnail(),
        dto.imageTitle(), dto.imageCredit(), dto.sourceUrl()));
    // To-many: left out means unchanged; given (even empty) means replaced, in place — Hibernate
    // tracks the collection, so it must not be swapped for a new one.
    if (dto.crewIds() != null) {
      var crew = new LinkedHashSet<>(resolve(dto.crewIds().ids(), astronauts, "astronaut"));
      m.getCrew().clear();
      m.getCrew().addAll(crew);
    }
    if (dto.gallery() != null) {
      m.getGallery().clear();
      dto.gallery().stream().map(Pictures::entity).forEach(m.getGallery()::add);
    }
  }
}
