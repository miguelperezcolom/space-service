package io.mateu.space.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

/** A launch site, with the coordinates a map needs. */
public record LaunchSiteDto(
    Long id,
    @NotBlank(message = "must not be blank") String name,
    String country,
    String region,
    String operator,
    BigDecimal latitude,
    BigDecimal longitude,
    Boolean active,
    String description,
    Integer launchCount,
    String wikidataId,
    String wikidataUrl,
    String url,
    String image,
    String imageLarge,
    String thumbnail,
    String imageTitle,
    String imageCredit,
    String sourceUrl) {}
