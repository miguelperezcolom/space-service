package io.mateu.space.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * A mission. It OWNS its crew (the join table lives here), so a single PUT re-points who flew it;
 * an astronaut's missions are read-only, the inverse of this.
 */
@Entity
@Table(name = "mission")
@Getter
@Setter
public class Mission {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank private String name;

  private String program;
  private String agency;

  /** CREWED, LUNAR_FLYBY, FLYBY, ORBITER, LANDER, ROVER, HELICOPTER, PROBE, TELESCOPE, SAMPLE_RETURN, IMPACTOR. */
  private String type;

  /** PLANNED, ACTIVE, COMPLETED, PARTIAL_SUCCESS, FAILED. */
  private String status;

  private LocalDate launchDate;
  private LocalDate endDate;

  @ManyToOne(fetch = FetchType.LAZY)
  private CelestialBody destination;

  @ManyToOne(fetch = FetchType.LAZY)
  private LaunchSite launchSite;

  @ManyToOne(fetch = FetchType.LAZY)
  private Spacecraft spacecraft;

  @ManyToMany
  @JoinTable(
      name = "mission_crew",
      joinColumns = @JoinColumn(name = "mission_id"),
      inverseJoinColumns = @JoinColumn(name = "astronaut_id"))
  @BatchSize(size = 100)
  private Set<Astronaut> crew = new LinkedHashSet<>();

  /** US dollars. */
  @Column(precision = 15, scale = 2)
  private BigDecimal costUsd;

  /** A short line (Wikidata's description). */
  @Column(length = 1000)
  private String summary;

  /** What the mission set out to do — rich text (HTML). */
  @Column(length = 8000)
  private String objectives;

  /** Highlighted on the home page. */
  private Boolean featured;

  private String wikidataId;

  @Embedded private Picture picture = new Picture();

  @ElementCollection
  @CollectionTable(name = "mission_image", joinColumns = @JoinColumn(name = "mission_id"))
  @OrderColumn(name = "position")
  @BatchSize(size = 100)
  private List<Picture> gallery = new ArrayList<>();
}
