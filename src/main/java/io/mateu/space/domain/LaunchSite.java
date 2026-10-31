package io.mateu.space.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/** A launch pad, with its coordinates — what puts it on a map. */
@Entity
@Table(name = "launch_site")
@Getter
@Setter
public class LaunchSite {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank private String name;

  private String country;

  /** The administrative area it lies in: Brevard County, French Guiana… */
  private String region;

  private String operator;

  @Column(precision = 9, scale = 5)
  private BigDecimal latitude;

  @Column(precision = 9, scale = 5)
  private BigDecimal longitude;

  /** Whether any launch in the seed left from it in the last ten years. */
  private Boolean active;

  @Column(length = 1000)
  private String description;

  private String wikidataId;

  @Embedded private Picture picture = new Picture();
}
