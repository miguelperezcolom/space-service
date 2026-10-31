package io.mateu.space.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * One node of a spacecraft's breakdown: a subsystem (Science payload, Power…) or what it holds (an
 * instrument, a component). The tree is spacecraft → subsystem → instrument.
 */
@Entity
@Table(name = "spacecraft_component")
@Getter
@Setter
public class SpacecraftComponent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank private String name;

  /** SUBSYSTEM, INSTRUMENT, COMPONENT. */
  private String kind;

  @Column(length = 1000)
  private String description;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  private Spacecraft spacecraft;

  @ManyToOne(fetch = FetchType.LAZY)
  private SpacecraftComponent parent;

  @OneToMany(mappedBy = "parent", cascade = CascadeType.REMOVE)
  @OrderBy("id")
  @BatchSize(size = 100)
  private List<SpacecraftComponent> children = new ArrayList<>();
}
