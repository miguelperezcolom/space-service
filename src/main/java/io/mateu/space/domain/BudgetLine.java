package io.mateu.space.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * What one program got in one fiscal year, in millions of US dollars. The yearly TOTALS follow
 * Wikidata's NASA budget figures (interpolated between the years it has); the split by program is
 * SYNTHETIC — plausible shares, generated deterministically by the seed script.
 */
@Entity
@Table(name = "budget_line")
@Getter
@Setter
public class BudgetLine {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Integer fiscalYear;

  private String program;

  /** SCIENCE, EXPLORATION, OPERATIONS, TECHNOLOGY, AERONAUTICS, EDUCATION, SUPPORT. */
  private String category;

  @Column(precision = 12, scale = 1)
  private BigDecimal amountMillionsUsd;

  private Boolean synthetic;

  @Column(length = 500)
  private String basis;
}
