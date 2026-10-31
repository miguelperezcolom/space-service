package io.mateu.space.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * The Sun, a planet, a dwarf planet, a moon, an asteroid. Bodies form a TREE through {@code parent}
 * (the Sun → Jupiter → Europa), which is how {@code /api/bodies/tree} answers.
 */
@Entity
@Table(name = "celestial_body")
@Getter
@Setter
public class CelestialBody {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank private String name;

  /** STAR, PLANET, DWARF_PLANET, MOON, ASTEROID, KUIPER_BELT_OBJECT. */
  private String type;

  @ManyToOne(fetch = FetchType.LAZY)
  private CelestialBody parent;

  @OneToMany(mappedBy = "parent")
  @OrderBy("id")
  @BatchSize(size = 100)
  private List<CelestialBody> children = new ArrayList<>();

  /** Mean radius in kilometres. */
  private BigDecimal radiusKm;

  /** Kilograms, as a double: the range runs from 10⁹ (Dimorphos) to 10³⁰ (the Sun). */
  private Double massKg;

  private BigDecimal orbitalPeriodDays;

  /** Surface gravity in m/s². */
  private BigDecimal surfaceGravity;

  @Column(length = 1000)
  private String description;

  private String wikidataId;

  @Embedded private Picture picture = new Picture();
}
