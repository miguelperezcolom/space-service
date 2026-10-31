package io.mateu.space.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/** One launch: when, on what, from where, carrying which mission, and how it went. */
@Entity
@Table(name = "launch")
@Getter
@Setter
public class Launch {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private LocalDate launchDate;

  /** Kept beside the date so a range or a calendar can query it; always the date's year. */
  private Integer launchYear;

  /** UTC, when known to the minute; null when only the day is. */
  private LocalTime launchTimeUtc;

  private String vehicle;

  @ManyToOne(fetch = FetchType.LAZY)
  private LaunchSite site;

  @ManyToOne(fetch = FetchType.LAZY)
  private Mission mission;

  /** SUCCESS, FAILURE, PARTIAL_FAILURE, SCHEDULED. */
  private String outcome;

  @Column(length = 1000)
  private String notes;

  public void setLaunchDate(LocalDate launchDate) {
    this.launchDate = launchDate;
    this.launchYear = launchDate == null ? null : launchDate.getYear();
  }
}
