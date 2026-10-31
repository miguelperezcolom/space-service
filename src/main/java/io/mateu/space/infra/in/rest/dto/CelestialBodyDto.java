package io.mateu.space.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

/**
 * A celestial body. In the flat collection {@code children} is null; in {@code /api/bodies/tree} it
 * holds the bodies that orbit it.
 */
public record CelestialBodyDto(
    Long id,
    @NotBlank(message = "must not be blank") String name,
    String type,
    Long parentId,
    String parentName,
    BigDecimal radiusKm,
    Double massKg,
    BigDecimal orbitalPeriodDays,
    BigDecimal surfaceGravity,
    String description,
    Integer missionCount,
    String wikidataId,
    String wikidataUrl,
    String url,
    String image,
    String imageLarge,
    String thumbnail,
    String imageTitle,
    String imageCredit,
    String sourceUrl,
    List<CelestialBodyDto> children) {}
