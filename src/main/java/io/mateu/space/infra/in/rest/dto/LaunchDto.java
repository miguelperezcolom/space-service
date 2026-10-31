package io.mateu.space.infra.in.rest.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A launch. {@code launchDateTime} is the date and the UTC time together (midnight when only the day
 * is known); on a write it wins over the two separate fields when given.
 */
public record LaunchDto(
    Long id,
    LocalDate launchDate,
    LocalTime launchTimeUtc,
    LocalDateTime launchDateTime,
    Integer launchYear,
    String vehicle,
    Long siteId,
    String siteName,
    Long missionId,
    String missionName,
    String outcome,
    String notes,
    String image,
    String url) {}
