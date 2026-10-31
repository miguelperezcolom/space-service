package io.mateu.space.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

/** A spacecraft on the wire. Its components are their own collection (/api/components). */
public record SpacecraftDto(
    Long id,
    @NotBlank(message = "must not be blank") String name,
    String type,
    String manufacturer,
    BigDecimal massKg,
    Integer powerWatts,
    String powerSource,
    String description,
    Integer componentCount,
    List<Long> missionIds,
    String wikidataId,
    String wikidataUrl,
    String url,
    String image,
    String imageLarge,
    String thumbnail,
    String imageTitle,
    String imageCredit,
    String sourceUrl) {}
