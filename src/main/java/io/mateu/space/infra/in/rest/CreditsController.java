package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.Picture;
import io.mateu.space.infra.out.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where every photograph comes from, and whom to credit. They are all NASA Image and Video Library
 * assets, linked where NASA publishes them; NASA media is generally not subject to copyright in the
 * United States, but its guidelines ask for the credit, and that NASA insignia not be used to imply
 * endorsement — the seed carries photographs, not logos. Read from the database, so a picture
 * written through the API is credited too.
 */
@RestController
@RequestMapping("/api/credits")
@Tag(name = "Credits", description = "Where each photograph comes from")
public class CreditsController {

  /** One photograph: the record it illustrates, and the asset it is. */
  public record Credit(String collection, Long id, String name, String title, String image,
      String credit, String sourceUrl) {}

  private final MissionRepository missions;
  private final AstronautRepository astronauts;
  private final SpacecraftRepository spacecraft;
  private final CelestialBodyRepository bodies;
  private final LaunchSiteRepository sites;

  public CreditsController(MissionRepository missions, AstronautRepository astronauts,
      SpacecraftRepository spacecraft, CelestialBodyRepository bodies, LaunchSiteRepository sites) {
    this.missions = missions;
    this.astronauts = astronauts;
    this.spacecraft = spacecraft;
    this.bodies = bodies;
    this.sites = sites;
  }

  @Operation(summary = "Every photograph in the dataset, with its credit and its source page")
  @Transactional(readOnly = true)
  @GetMapping
  public List<Credit> credits() {
    List<Credit> out = new ArrayList<>();
    Sort byId = Sort.by("id");
    missions.findAll(byId).forEach(m -> {
      add(out, "missions", m.getId(), m.getName(), m.getPicture());
      m.getGallery().forEach(g -> add(out, "missions", m.getId(), m.getName(), g));
    });
    astronauts.findAll(byId).forEach(a -> add(out, "astronauts", a.getId(), a.getName(), a.getPicture()));
    spacecraft.findAll(byId).forEach(s -> add(out, "spacecraft", s.getId(), s.getName(), s.getPicture()));
    bodies.findAll(byId).forEach(b -> add(out, "bodies", b.getId(), b.getName(), b.getPicture()));
    sites.findAll(byId).forEach(s -> add(out, "launch-sites", s.getId(), s.getName(), s.getPicture()));
    return out.stream().distinct().toList();
  }

  private static void add(List<Credit> out, String collection, Long id, String name, Picture p) {
    if (p == null || p.getImage() == null) return;
    out.add(new Credit(collection, id, name, p.getImageTitle(), p.getImage(), p.getImageCredit(),
        p.getSourceUrl()));
  }
}
