package io.mateu.space.seed;

import io.mateu.space.domain.*;
import io.mateu.space.infra.out.*;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Loads the bundled dataset (src/main/resources/seed/*.json, written by scripts/build-seed.py) into
 * the database at boot.
 *
 * <p>The database is H2 in memory, so this runs on every start and every restart is a clean slate —
 * which is the point: a writable demo that anybody can scribble on had better be able to forget.
 *
 * <p>The seed references rows by their seed id. Collections are loaded in dependency order (a
 * mission needs its destination, site, spacecraft and crew first), and the two self-references —
 * a body's parent, a component's parent — are linked in a second pass, once every row exists.
 */
@Component
public class SpaceSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SpaceSeeder.class);

  private final JsonMapper json = JsonMapper.builder().build();

  private final CelestialBodyRepository bodies;
  private final LaunchSiteRepository sites;
  private final SpacecraftRepository spacecraft;
  private final SpacecraftComponentRepository components;
  private final AstronautRepository astronauts;
  private final MissionRepository missions;
  private final LaunchRepository launches;
  private final MissionTaskRepository tasks;
  private final BudgetLineRepository budgets;

  /** Seed id → the row it became. */
  private final Map<Long, CelestialBody> bodyById = new HashMap<>();
  private final Map<Long, LaunchSite> siteById = new HashMap<>();
  private final Map<Long, Spacecraft> spacecraftById = new HashMap<>();
  private final Map<Long, SpacecraftComponent> componentById = new HashMap<>();
  private final Map<Long, Astronaut> astronautById = new HashMap<>();
  private final Map<Long, Mission> missionById = new HashMap<>();

  public SpaceSeeder(
      CelestialBodyRepository bodies,
      LaunchSiteRepository sites,
      SpacecraftRepository spacecraft,
      SpacecraftComponentRepository components,
      AstronautRepository astronauts,
      MissionRepository missions,
      LaunchRepository launches,
      MissionTaskRepository tasks,
      BudgetLineRepository budgets) {
    this.bodies = bodies;
    this.sites = sites;
    this.spacecraft = spacecraft;
    this.components = components;
    this.astronauts = astronauts;
    this.missions = missions;
    this.launches = launches;
    this.tasks = tasks;
    this.budgets = budgets;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) throws IOException {
    if (missions.count() > 0) {
      log.info("Database already seeded; skipping");
      return;
    }
    long started = System.currentTimeMillis();

    forEach("bodies", n -> bodyById.put(id(n), bodies.save(body(n))));
    forEach("bodies", n -> bodyById.get(id(n)).setParent(bodyById.get(ref(n, "parentId"))));
    forEach("launch-sites", n -> siteById.put(id(n), sites.save(site(n))));
    forEach("spacecraft", n -> spacecraftById.put(id(n), spacecraft.save(spacecraft(n))));
    forEach("components", n -> componentById.put(id(n), components.save(component(n))));
    forEach("components", n -> componentById.get(id(n)).setParent(componentById.get(ref(n, "parentId"))));
    forEach("astronauts", n -> astronautById.put(id(n), astronauts.save(astronaut(n))));
    forEach("missions", n -> missionById.put(id(n), missions.save(mission(n))));
    forEach("launches", n -> launches.save(launch(n)));
    forEach("tasks", n -> tasks.save(task(n)));
    forEach("budgets", n -> budgets.save(budget(n)));

    log.info(
        "Seeded {} missions, {} astronauts, {} spacecraft ({} components), {} bodies, {} launch sites,"
            + " {} launches, {} tasks, {} budget lines in {} ms",
        missions.count(), astronauts.count(), spacecraft.count(), components.count(), bodies.count(),
        sites.count(), launches.count(), tasks.count(), budgets.count(),
        System.currentTimeMillis() - started);
  }

  // ── builders ────────────────────────────────────────────────────────────────

  private CelestialBody body(JsonNode n) {
    CelestialBody b = new CelestialBody();
    b.setName(text(n, "name"));
    b.setType(text(n, "type"));
    b.setRadiusKm(decimal(n, "radiusKm"));
    b.setMassKg(n.hasNonNull("massKg") ? n.get("massKg").asDouble() : null);
    b.setOrbitalPeriodDays(decimal(n, "orbitalPeriodDays"));
    b.setSurfaceGravity(decimal(n, "surfaceGravity"));
    b.setDescription(text(n, "description"));
    b.setWikidataId(text(n, "wikidataId"));
    b.setPicture(picture(n));
    return b;
  }

  private LaunchSite site(JsonNode n) {
    LaunchSite s = new LaunchSite();
    s.setName(text(n, "name"));
    s.setCountry(text(n, "country"));
    s.setRegion(text(n, "region"));
    s.setOperator(text(n, "operator"));
    s.setLatitude(decimal(n, "latitude"));
    s.setLongitude(decimal(n, "longitude"));
    s.setActive(n.path("active").asBoolean(false));
    s.setDescription(text(n, "description"));
    s.setWikidataId(text(n, "wikidataId"));
    s.setPicture(picture(n));
    return s;
  }

  private Spacecraft spacecraft(JsonNode n) {
    Spacecraft s = new Spacecraft();
    s.setName(text(n, "name"));
    s.setType(text(n, "type"));
    s.setManufacturer(text(n, "manufacturer"));
    s.setMassKg(decimal(n, "massKg"));
    s.setPowerWatts(integer(n, "powerWatts"));
    s.setPowerSource(text(n, "powerSource"));
    s.setDescription(text(n, "description"));
    s.setWikidataId(text(n, "wikidataId"));
    s.setPicture(picture(n));
    return s;
  }

  private SpacecraftComponent component(JsonNode n) {
    SpacecraftComponent c = new SpacecraftComponent();
    c.setName(text(n, "name"));
    c.setKind(text(n, "kind"));
    c.setDescription(text(n, "description"));
    Spacecraft owner = spacecraftById.get(ref(n, "spacecraftId"));
    c.setSpacecraft(owner);
    owner.getComponents().add(c);
    return c;
  }

  private Astronaut astronaut(JsonNode n) {
    Astronaut a = new Astronaut();
    a.setName(text(n, "name"));
    a.setNationality(text(n, "nationality"));
    a.setGender(text(n, "gender"));
    a.setBirthDate(date(n, "birthDate"));
    a.setDeathDate(date(n, "deathDate"));
    a.setBirthPlace(text(n, "birthPlace"));
    a.setSelectionGroup(text(n, "selectionGroup"));
    a.setSelectionYear(integer(n, "selectionYear"));
    a.setStatus(text(n, "status"));
    a.setTimeInSpaceDays(decimal(n, "timeInSpaceDays"));
    a.setEvaHours(decimal(n, "evaHours"));
    a.setSummary(text(n, "summary"));
    a.setBio(text(n, "bio"));
    a.setWikidataId(text(n, "wikidataId"));
    a.setPicture(picture(n));
    return a;
  }

  private Mission mission(JsonNode n) {
    Mission m = new Mission();
    m.setName(text(n, "name"));
    m.setProgram(text(n, "program"));
    m.setAgency(text(n, "agency"));
    m.setType(text(n, "type"));
    m.setStatus(text(n, "status"));
    m.setLaunchDate(date(n, "launchDate"));
    m.setEndDate(date(n, "endDate"));
    m.setDestination(bodyById.get(ref(n, "destinationId")));
    m.setLaunchSite(siteById.get(ref(n, "launchSiteId")));
    Spacecraft craft = spacecraftById.get(ref(n, "spacecraftId"));
    m.setSpacecraft(craft);
    if (craft != null) craft.getMissions().add(m);
    m.setCostUsd(decimal(n, "costUsd"));
    m.setSummary(text(n, "summary"));
    m.setObjectives(text(n, "objectives"));
    m.setFeatured(n.path("featured").asBoolean(false));
    m.setWikidataId(text(n, "wikidataId"));
    m.setPicture(picture(n));
    n.path("crewIds").forEach(id -> {
      Astronaut a = astronautById.get(id.asLong());
      if (a != null) {
        m.getCrew().add(a);
        a.getMissions().add(m);
      }
    });
    n.path("gallery").forEach(g -> m.getGallery().add(picture(g)));
    return m;
  }

  private Launch launch(JsonNode n) {
    Launch l = new Launch();
    l.setLaunchDate(date(n, "launchDate"));
    String time = text(n, "launchTimeUtc");
    l.setLaunchTimeUtc(time == null ? null : LocalTime.parse(time));
    l.setVehicle(text(n, "vehicle"));
    l.setSite(siteById.get(ref(n, "siteId")));
    l.setMission(missionById.get(ref(n, "missionId")));
    l.setOutcome(text(n, "outcome"));
    l.setNotes(text(n, "notes"));
    return l;
  }

  private MissionTask task(JsonNode n) {
    MissionTask t = new MissionTask();
    t.setTitle(text(n, "title"));
    t.setMission(missionById.get(ref(n, "missionId")));
    t.setPhase(text(n, "phase"));
    t.setStatus(text(n, "status"));
    t.setOwner(text(n, "owner"));
    t.setPriority(text(n, "priority"));
    t.setDueDate(date(n, "dueDate"));
    t.setEstimateDays(integer(n, "estimateDays"));
    t.setProgress(integer(n, "progress"));
    t.setDescription(text(n, "description"));
    return t;
  }

  private BudgetLine budget(JsonNode n) {
    BudgetLine b = new BudgetLine();
    b.setFiscalYear(integer(n, "year"));
    b.setProgram(text(n, "program"));
    b.setCategory(text(n, "category"));
    b.setAmountMillionsUsd(decimal(n, "amountMillionsUsd"));
    b.setSynthetic(n.path("synthetic").asBoolean(true));
    b.setBasis(text(n, "basis"));
    return b;
  }

  private static Picture picture(JsonNode n) {
    return new Picture(text(n, "image"), text(n, "imageLarge"), text(n, "thumbnail"),
        text(n, "imageTitle") != null ? text(n, "imageTitle") : text(n, "title"),
        text(n, "imageCredit"), text(n, "sourceUrl"));
  }

  // ── reading ─────────────────────────────────────────────────────────────────

  private void forEach(String resource, Consumer<JsonNode> visitor) throws IOException {
    try (InputStream in = new ClassPathResource("seed/" + resource + ".json").getInputStream()) {
      json.readTree(in).forEach(visitor);
    }
  }

  private static long id(JsonNode n) {
    return n.path("id").asLong();
  }

  private static Long ref(JsonNode n, String field) {
    return n.hasNonNull(field) ? n.get(field).asLong() : null;
  }

  private static String text(JsonNode n, String field) {
    if (!n.hasNonNull(field)) return null;
    String value = n.get(field).asString();
    return value == null || value.isBlank() ? null : value;
  }

  private static Integer integer(JsonNode n, String field) {
    return n.hasNonNull(field) ? (int) n.get(field).asDouble() : null;
  }

  private static BigDecimal decimal(JsonNode n, String field) {
    return n.hasNonNull(field) ? new BigDecimal(n.get(field).asString()) : null;
  }

  private static LocalDate date(JsonNode n, String field) {
    String value = text(n, field);
    return value == null ? null : LocalDate.parse(value);
  }
}
