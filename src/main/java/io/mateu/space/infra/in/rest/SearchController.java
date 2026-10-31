package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.Picture;
import io.mateu.space.infra.in.rest.query.PageResponse;
import io.mateu.space.infra.out.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One search box over every collection: missions, astronauts, spacecraft, celestial bodies and
 * launch sites answer together, each hit carrying a {@code route} (`missions/6`) a client can open.
 * The dataset is a few hundred rows, so the hits are gathered and ranked in memory — a name that
 * starts with the text first, then one that contains it, then a match elsewhere.
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Search across every collection")
public class SearchController {

  public record Hit(String kind, Long id, String name, String subtitle, String image, String route,
      int rank) {}

  static final List<String> KINDS = List.of("MISSION", "ASTRONAUT", "SPACECRAFT", "BODY", "LAUNCH_SITE");

  private final MissionRepository missions;
  private final AstronautRepository astronauts;
  private final SpacecraftRepository spacecraft;
  private final CelestialBodyRepository bodies;
  private final LaunchSiteRepository sites;

  public SearchController(MissionRepository missions, AstronautRepository astronauts,
      SpacecraftRepository spacecraft, CelestialBodyRepository bodies, LaunchSiteRepository sites) {
    this.missions = missions;
    this.astronauts = astronauts;
    this.spacecraft = spacecraft;
    this.bodies = bodies;
    this.sites = sites;
  }

  @Operation(summary = "Search every collection at once")
  @Transactional(readOnly = true)
  @GetMapping
  public PageResponse<Hit> search(
      @Parameter(description = "The text; blank answers everything") @RequestParam(required = false) String search,
      @Parameter(description = "MISSION, ASTRONAUT, SPACECRAFT, BODY, LAUNCH_SITE — repeat or comma-join for several")
          @RequestParam(required = false) List<String> kind,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
    Set<String> kinds = kind == null ? Set.of() : kind.stream()
        .flatMap(k -> Arrays.stream(k.split(",")))
        .map(String::trim).filter(k -> !k.isEmpty())
        .map(k -> CrudController.oneOf(k, "kind", KINDS))
        .collect(Collectors.toSet());
    List<Hit> hits = new ArrayList<>();
    if (wants(kinds, "MISSION")) missions.findAll().forEach(m -> hit(hits, q, "MISSION", m.getId(), m.getName(),
        join(m.getProgram(), m.getLaunchDate() == null ? null : String.valueOf(m.getLaunchDate().getYear()), m.getStatus()),
        m.getPicture(), "missions/" + m.getId(), m.getSummary(), m.getAgency()));
    if (wants(kinds, "ASTRONAUT")) astronauts.findAll().forEach(a -> hit(hits, q, "ASTRONAUT", a.getId(), a.getName(),
        join(a.getNationality(), a.getSelectionGroup()), a.getPicture(), "astronauts/" + a.getId(), a.getSummary(), a.getBirthPlace()));
    if (wants(kinds, "SPACECRAFT")) spacecraft.findAll().forEach(s -> hit(hits, q, "SPACECRAFT", s.getId(), s.getName(),
        join(s.getType(), s.getManufacturer()), s.getPicture(), "spacecraft/" + s.getId(), s.getDescription(), null));
    if (wants(kinds, "BODY")) bodies.findAll().forEach(b -> hit(hits, q, "BODY", b.getId(), b.getName(),
        join(b.getType(), b.getParent() == null ? null : "orbits " + b.getParent().getName()), b.getPicture(),
        "bodies/" + b.getId(), b.getDescription(), null));
    if (wants(kinds, "LAUNCH_SITE")) sites.findAll().forEach(s -> hit(hits, q, "LAUNCH_SITE", s.getId(), s.getName(),
        join(s.getCountry(), s.getRegion()), s.getPicture(), "launch-sites/" + s.getId(), s.getOperator(), null));
    hits.sort(Comparator.comparingInt(Hit::rank).thenComparing(Hit::name, String.CASE_INSENSITIVE_ORDER));
    int pageSize = Math.min(Math.max(size, 1), 200);
    int from = Math.min(Math.max(page, 0) * pageSize, hits.size());
    int to = Math.min(from + pageSize, hits.size());
    return new PageResponse<>(hits.subList(from, to), hits.size(),
        (int) Math.ceil(hits.size() / (double) pageSize), Math.max(page, 0), pageSize);
  }

  private static boolean wants(Set<String> kinds, String kind) {
    return kinds.isEmpty() || kinds.contains(kind);
  }

  private static void hit(List<Hit> hits, String q, String kind, Long id, String name, String subtitle,
      Picture picture, String route, String... more) {
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    int rank;
    if (q.isEmpty() || n.startsWith(q)) rank = 0;
    else if (n.contains(q)) rank = 1;
    else if ((subtitle != null && subtitle.toLowerCase(Locale.ROOT).contains(q))
        || Arrays.stream(more).anyMatch(m -> m != null && m.toLowerCase(Locale.ROOT).contains(q))) rank = 2;
    else return;
    hits.add(new Hit(kind, id, name, subtitle,
        picture == null ? null : (picture.getThumbnail() != null ? picture.getThumbnail() : picture.getImage()),
        route, rank));
  }

  private static String join(String... parts) {
    return Arrays.stream(parts).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" · "));
  }
}
