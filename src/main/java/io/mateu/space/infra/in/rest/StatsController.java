package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.BudgetLine;
import io.mateu.space.domain.Launch;
import io.mateu.space.domain.Mission;
import io.mateu.space.infra.out.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dataset in numbers — counts, and the series a dashboard charts (launches per decade and per
 * year, missions by status, type and destination, budget per year and category, tasks by status).
 * Computed from the database on every call, so it follows whatever has been written.
 */
@RestController
@RequestMapping("/api/stats")
@Tag(name = "Stats", description = "Counts and chart series")
public class StatsController {

  /** One bar, slice or point of a series. */
  public record Point(String label, BigDecimal value) {}

  private final MissionRepository missions;
  private final AstronautRepository astronauts;
  private final SpacecraftRepository spacecraft;
  private final LaunchRepository launches;
  private final LaunchSiteRepository sites;
  private final CelestialBodyRepository bodies;
  private final MissionTaskRepository tasks;
  private final BudgetLineRepository budgets;

  public StatsController(MissionRepository missions, AstronautRepository astronauts,
      SpacecraftRepository spacecraft, LaunchRepository launches, LaunchSiteRepository sites,
      CelestialBodyRepository bodies, MissionTaskRepository tasks, BudgetLineRepository budgets) {
    this.missions = missions;
    this.astronauts = astronauts;
    this.spacecraft = spacecraft;
    this.launches = launches;
    this.sites = sites;
    this.bodies = bodies;
    this.tasks = tasks;
    this.budgets = budgets;
  }

  @Operation(summary = "Counts and chart series over the whole dataset")
  @Transactional(readOnly = true)
  @GetMapping
  public Map<String, Object> stats() {
    List<Mission> allMissions = missions.findAll();
    List<Launch> allLaunches = launches.findAll();
    List<BudgetLine> allBudgets = budgets.findAll();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("missions", allMissions.size());
    out.put("activeMissions", allMissions.stream().filter(m -> "ACTIVE".equals(m.getStatus())).count());
    out.put("crewedMissions", allMissions.stream().filter(m -> "CREWED".equals(m.getType())).count());
    out.put("astronauts", astronauts.count());
    out.put("spacecraft", spacecraft.count());
    out.put("launches", allLaunches.size());
    long flown = allLaunches.stream().filter(l -> !"SCHEDULED".equals(l.getOutcome())).count();
    long ok = allLaunches.stream().filter(l -> "SUCCESS".equals(l.getOutcome())).count();
    out.put("launchSuccessRate", flown == 0 ? null : BigDecimal.valueOf(ok * 1000 / flown, 1));
    out.put("launchSites", sites.count());
    out.put("bodies", bodies.count());
    out.put("tasks", tasks.count());
    out.put("missionCostUsd", allMissions.stream().map(Mission::getCostUsd)
        .filter(c -> c != null).reduce(BigDecimal.ZERO, BigDecimal::add));
    out.put("launchesByDecade", count(allLaunches.stream().filter(l -> l.getLaunchYear() != null),
        l -> (l.getLaunchYear() / 10 * 10) + "s", true));
    out.put("launchesByYear", count(allLaunches.stream().filter(l -> l.getLaunchYear() != null),
        l -> String.valueOf(l.getLaunchYear()), true));
    out.put("launchesByOutcome", count(allLaunches.stream(), Launch::getOutcome, false));
    out.put("missionsByStatus", count(allMissions.stream(), Mission::getStatus, false));
    out.put("missionsByType", count(allMissions.stream(), Mission::getType, false));
    out.put("missionsByProgram", count(allMissions.stream(), Mission::getProgram, false));
    out.put("missionsByDestination", count(allMissions.stream().filter(m -> m.getDestination() != null),
        m -> m.getDestination().getName(), false));
    out.put("tasksByStatus", count(tasks.findAll().stream(), t -> t.getStatus(), false));
    out.put("budgetByYear", sum(allBudgets, b -> String.valueOf(b.getFiscalYear()), true));
    Integer latest = allBudgets.stream().map(BudgetLine::getFiscalYear).filter(y -> y != null)
        .max(Integer::compare).orElse(null);
    out.put("budgetYear", latest);
    out.put("budgetByProgram", sum(allBudgets.stream().filter(b -> latest != null && latest.equals(b.getFiscalYear())).toList(),
        BudgetLine::getProgram, false));
    return out;
  }

  private static <T> List<Point> count(java.util.stream.Stream<T> rows, Function<T, String> key, boolean byKey) {
    Map<String, Long> counted = rows.filter(r -> key.apply(r) != null)
        .collect(Collectors.groupingBy(key, TreeMap::new, Collectors.counting()));
    return counted.entrySet().stream()
        .sorted(byKey ? Map.Entry.comparingByKey() : Map.Entry.<String, Long>comparingByValue().reversed())
        .map(e -> new Point(e.getKey(), BigDecimal.valueOf(e.getValue())))
        .toList();
  }

  private static List<Point> sum(List<BudgetLine> rows, Function<BudgetLine, String> key, boolean byKey) {
    Map<String, BigDecimal> summed = rows.stream().filter(r -> key.apply(r) != null && r.getAmountMillionsUsd() != null)
        .collect(Collectors.groupingBy(key, TreeMap::new,
            Collectors.reducing(BigDecimal.ZERO, BudgetLine::getAmountMillionsUsd, BigDecimal::add)));
    return summed.entrySet().stream()
        .sorted(byKey ? Map.Entry.comparingByKey() : Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
        .map(e -> new Point(e.getKey(), e.getValue()))
        .toList();
  }
}
