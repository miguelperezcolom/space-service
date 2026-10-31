package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.Launch;
import io.mateu.space.infra.in.rest.dto.LaunchDto;
import io.mateu.space.infra.in.rest.query.FilterKind;
import io.mateu.space.infra.in.rest.query.QuerySpec;
import io.mateu.space.infra.out.LaunchRepository;
import io.mateu.space.infra.out.LaunchSiteRepository;
import io.mateu.space.infra.out.MissionRepository;
import io.mateu.space.infra.out.SpaceRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/launches")
@Tag(name = "Launches", description = "When, on what and from where each mission left the ground")
public class LaunchesController extends CrudController<Launch, LaunchDto> {

  static final List<String> OUTCOMES = List.of("SUCCESS", "FAILURE", "PARTIAL_FAILURE", "SCHEDULED");

  private final LaunchRepository launches;
  private final LaunchSiteRepository sites;
  private final MissionRepository missions;

  public LaunchesController(
      LaunchRepository launches, LaunchSiteRepository sites, MissionRepository missions) {
    this.launches = launches;
    this.sites = sites;
    this.missions = missions;
  }

  @Override
  protected String collection() {
    return "launches";
  }

  @Override
  protected SpaceRepository<Launch> repository() {
    return launches;
  }

  @Override
  protected java.util.Map<String, String> sortAliases() {
    return java.util.Map.of("siteName", "site.name", "missionName", "mission.name", "launchDateTime", "launchDate");
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("vehicle", FilterKind.TEXT)
        .filter("outcome", FilterKind.EXACT)
        .filter("launchDate", FilterKind.DATE)
        .filter("launchYear", FilterKind.NUMBER)
        .filter("siteId", "site.id", FilterKind.REF)
        .filter("missionId", "mission.id", FilterKind.REF)
        .searchable("vehicle", "mission.name", "site.name", "notes")
        .defaultSort("launchDate")
        .build();
  }

  @Override
  protected Launch newEntity() {
    return new Launch();
  }

  @Override
  protected Long idOf(LaunchDto dto) {
    return dto.id();
  }

  @Override
  protected LaunchDto toDto(Launch l) {
    return new LaunchDto(
        l.getId(),
        l.getLaunchDate(),
        l.getLaunchTimeUtc(),
        l.getLaunchDate() == null
            ? null
            : LocalDateTime.of(l.getLaunchDate(), l.getLaunchTimeUtc() == null ? LocalTime.MIDNIGHT : l.getLaunchTimeUtc()),
        l.getLaunchYear(),
        l.getVehicle(),
        l.getSite() == null ? null : l.getSite().getId(),
        l.getSite() == null ? null : l.getSite().getName(),
        l.getMission() == null ? null : l.getMission().getId(),
        l.getMission() == null ? null : l.getMission().getName(),
        l.getOutcome(),
        l.getNotes(),
        l.getMission() == null || l.getMission().getPicture() == null
            ? null
            : l.getMission().getPicture().getThumbnail(),
        urlOf(l.getId()));
  }

  @Override
  protected void apply(LaunchDto dto, Launch l) {
    if (dto.launchDateTime() != null) {
      l.setLaunchDate(dto.launchDateTime().toLocalDate());
      LocalTime time = dto.launchDateTime().toLocalTime();
      l.setLaunchTimeUtc(time.equals(LocalTime.MIDNIGHT) && dto.launchTimeUtc() == null ? null : time);
    } else {
      l.setLaunchDate(dto.launchDate());
      l.setLaunchTimeUtc(dto.launchTimeUtc());
    }
    l.setVehicle(dto.vehicle());
    l.setSite(resolveOne(dto.siteId(), sites, "launch site"));
    l.setMission(resolveOne(dto.missionId(), missions, "mission"));
    l.setOutcome(oneOf(dto.outcome(), "outcome", OUTCOMES));
    l.setNotes(dto.notes());
  }
}
