package io.mateu.space.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** A piece of work left on a mission in preparation — a card on the board. SYNTHETIC data. */
@Entity
@Table(name = "mission_task")
@Getter
@Setter
public class MissionTask {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank private String title;

  @ManyToOne(fetch = FetchType.LAZY)
  private Mission mission;

  /** DESIGN, BUILD, INTEGRATION, TEST, LAUNCH_OPS. */
  private String phase;

  /** BACKLOG, IN_PROGRESS, TESTING, DONE — the board's columns. */
  private String status;

  /** A team, not a person: the seed invents no people. */
  private String owner;

  /** LOW, MEDIUM, HIGH, CRITICAL. */
  private String priority;

  private LocalDate dueDate;

  private Integer estimateDays;

  /** 0–100. */
  private Integer progress;

  @Column(length = 2000)
  private String description;
}
