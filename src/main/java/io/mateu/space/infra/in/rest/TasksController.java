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

  /** The gap between neighbouring cards when a column is numbered (seed, a new card). */
  public static final double ORDER_STEP = 1000;

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
        .filter("boardOrder", FilterKind.NUMBER)
        .searchable("title", "owner", "description", "mission.name")
        .defaultSort("dueDate")
        .build();
  }

  @Operation(
      summary = "Move a card",
      description = "Sets only the status (and the progress a status implies: 0 in the backlog, 100 "
          + "when done) and, when given, the card's place in the column (boardOrder) — what dragging "
          + "a card means. Without boardOrder a card entering another column goes to its end. "
          + "Requires the write API key.")
  @Transactional
  @PostMapping("/{id}/move")
  public TaskDto move(
      @PathVariable Long id,
      @RequestParam String status,
      @RequestParam(required = false) Double boardOrder) {
    MissionTask t = tasks.findById(id).orElseThrow(() -> new NotFoundException(collection(), id));
    boolean changesColumn = !java.util.Objects.equals(t.getStatus(), status);
    t.setStatus(oneOf(status, "status", STATUSES));
    if (boardOrder != null) {
      t.setBoardOrder(boardOrder);
    } else if (changesColumn) {
      t.setBoardOrder(endOfColumn(t));
    }
    if ("BACKLOG".equals(t.getStatus())) t.setProgress(0);
    if ("DONE".equals(t.getStatus())) t.setProgress(100);
    return toDto(t);
  }

  @Override
  protected void onCreate(MissionTask t) {
    if (t.getStatus() == null) t.setStatus("BACKLOG");
    if (t.getProgress() == null) t.setProgress(0);
  }

  /** One step past the last card of the task's column. */
  private double endOfColumn(MissionTask t) {
    return tasks.findAll().stream()
            .filter(o -> o != t && java.util.Objects.equals(o.getStatus(), t.getStatus()) && o.getBoardOrder() != null)
            .mapToDouble(MissionTask::getBoardOrder)
            .max()
            .orElse(0)
        + ORDER_STEP;
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
        t.getBoardOrder(),
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
    // a write that names no order (an edit form) keeps the card where it is; a new card goes last
    if (dto.boardOrder() != null) {
      t.setBoardOrder(dto.boardOrder());
    } else if (t.getBoardOrder() == null) {
      t.setBoardOrder(endOfColumn(t));
    }
  }
}
