package io.mateu.space.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** Someone who flew. The missions they flew are the inverse of a mission's crew. */
@Entity
@Table(name = "astronaut")
@Getter
@Setter
public class Astronaut {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank private String name;

  private String nationality;
  private String gender;
  private LocalDate birthDate;
  private LocalDate deathDate;
  private String birthPlace;

  /** NASA Astronaut Group 2, Mercury Seven… */
  private String selectionGroup;

  private Integer selectionYear;

  /** ACTIVE, RETIRED, DECEASED. */
  private String status;

  private BigDecimal timeInSpaceDays;

  /** Approximate total hours of spacewalks, where well documented; null elsewhere. */
  private BigDecimal evaHours;

  /** A short line (Wikidata's description). */
  @Column(length = 1000)
  private String summary;

  /** A longer text, HTML. */
  @Column(length = 4000)
  private String bio;

  private String wikidataId;

  @Embedded private Picture picture = new Picture();

  @ManyToMany(mappedBy = "crew")
  @OrderBy("launchDate")
  @BatchSize(size = 100)
  private Set<Mission> missions = new LinkedHashSet<>();
}
