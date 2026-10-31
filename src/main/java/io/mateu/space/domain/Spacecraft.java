package io.mateu.space.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** A capsule, an orbiter, a rover, a telescope… The vehicle a mission flies. */
@Entity
@Table(name = "spacecraft")
@Getter
@Setter
public class Spacecraft {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank private String name;

  /** CAPSULE, LANDER, ORBITER, ROVER, PROBE, TELESCOPE, STATION, HELICOPTER. */
  private String type;

  private String manufacturer;

  private BigDecimal massKg;

  private Integer powerWatts;

  /** SOLAR, RTG, FUEL_CELL, BATTERY. */
  private String powerSource;

  @Column(length = 1000)
  private String description;

  private String wikidataId;

  @Embedded private Picture picture = new Picture();

  /** The whole tree, flat; {@link SpacecraftComponent#getParent()} gives it its shape. */
  @OneToMany(mappedBy = "spacecraft", cascade = CascadeType.REMOVE)
  @OrderBy("id")
  @BatchSize(size = 100)
  private List<SpacecraftComponent> components = new ArrayList<>();

  @OneToMany(mappedBy = "spacecraft")
  @BatchSize(size = 100)
  private List<Mission> missions = new ArrayList<>();
}
