package io.mateu.space.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * An astronaut on the wire. The missions they flew ({@code missionIds}/{@code missionNames}) are
 * read-only here: a crew is written on the mission, which owns it. Picture fields left out of a
 * write keep their value.
 */
public record AstronautDto(
    Long id,
    @NotBlank(message = "must not be blank") String name,
    String nationality,
    String gender,
    LocalDate birthDate,
    LocalDate deathDate,
    String birthPlace,
    String selectionGroup,
    Integer selectionYear,
    String status,
    BigDecimal timeInSpaceDays,
    BigDecimal evaHours,
    String summary,
    String bio,
    List<Long> missionIds,
    List<String> missionNames,
    Integer flights,
    String wikidataId,
    String wikidataUrl,
    String url,
    String image,
    String imageLarge,
    String thumbnail,
    String imageTitle,
    String imageCredit,
    String sourceUrl) {}
