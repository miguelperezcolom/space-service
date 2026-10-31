package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.MissionTask;
import io.mateu.space.infra.in.rest.dto.TaskDto;
import io.mateu.space.infra.in.rest.query.FilterKind;
import io.mateu.space.infra.in.rest.query.QuerySpec;
import io.mateu.space.infra.out.MissionRepository;
import io.mateu.space.infra.out.MissionTaskRepository;
import io.mateu.space.infra.out.SpaceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** The work left on the missions in preparation: a board's cards. SYNTHETIC data. */
@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Mission tasks", description = "Backlog → in progress → testing → done (synthetic)")
public class TasksController extends CrudController<MissionTask, TaskDto> {

  static final List<String> STATUSES = List.of("BACKLOG", "IN_PROGRESS", "TESTING", "DONE");
  static final List<String> PHASES = List.of("DESIGN", "BUILD", "INTEGRATION", "TEST", "LAUNCH_OPS");
  static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

  private final MissionTaskRepository tasks;
  private final MissionRepository missions;

  public TasksController(MissionTaskRepository tasks, MissionRepository missions) {
    this.tasks = tasks;
    this.missions = missions;
  }

  @Override
  protected String collection() {
    return "tasks";
  }

  @Override
  protected SpaceRepository<MissionTask> repository() {
    return tasks;
  }

  @Override
  protected java.util.Map<String, String> sortAliases() {
    return java.util.Map.of("missionName", "mission.name");
  }

  @Override
  protected QuerySpec querySpec() {
    return QuerySpec.builder()
        .filter("title", FilterKind.TEXT)
        .filter("status", FilterKind.EXACT)
        .filter("phase", FilterKind.EXACT)
        .filter("priority", FilterKind.EXACT)
        .filter("owner", FilterKind.EXACT)
        .filter("missionId", "mission.id", FilterKind.REF)
        .filter("dueDate", FilterKind.DATE)
        .filter("progress", FilterKind.NUMBER)
        .searchable("title", "owner", "description", "mission.name")
        .defaultSort("dueDate")
        .build();
  }

  @Operation(
      summary = "Move a card",
      description = "Sets only the status (and the progress a status implies: 0 in the backlog, 100 "
          + "when done) — what dragging a card to another column means. Requires the write API key.")
  @Transactional
  @PostMapping("/{id}/move")
  public TaskDto move(@PathVariable Long id, @RequestParam String status) {
    MissionTask t = tasks.findById(id).orElseThrow(() -> new NotFoundException(collection(), id));
    t.setStatus(oneOf(status, "status", STATUSES));
    if ("BACKLOG".equals(t.getStatus())) t.setProgress(0);
    if ("DONE".equals(t.getStatus())) t.setProgress(100);
    return toDto(t);
  }

  @Override
  protected MissionTask newEntity() {
    return new MissionTask();
  }

  @Override
  protected Long idOf(TaskDto dto) {
    return dto.id();
  }

  @Override
  protected TaskDto toDto(MissionTask t) {
    return new TaskDto(
        t.getId(),
        t.getTitle(),
        t.getMission() == null ? null : t.getMission().getId(),
        t.getMission() == null ? null : t.getMission().getName(),
        t.getPhase(),
        t.getStatus(),
        t.getOwner(),
        t.getPriority(),
        t.getDueDate(),
        t.getEstimateDays(),
        t.getProgress(),
        t.getDescription(),
        urlOf(t.getId()));
  }

  @Override
  protected void apply(TaskDto dto, MissionTask t) {
    t.setTitle(dto.title());
    t.setMission(resolveOne(dto.missionId(), missions, "mission"));
    t.setPhase(oneOf(dto.phase(), "phase", PHASES));
    t.setStatus(oneOf(dto.status(), "status", STATUSES));
    t.setOwner(dto.owner());
    t.setPriority(oneOf(dto.priority(), "priority", PRIORITIES));
    t.setDueDate(dto.dueDate());
    t.setEstimateDays(dto.estimateDays());
    if (dto.progress() != null && (dto.progress() < 0 || dto.progress() > 100)) {
      throw new InvalidValueException("progress", String.valueOf(dto.progress()), List.of("0 to 100"));
    }
    t.setProgress(dto.progress());
    t.setDescription(dto.description());
  }
}
