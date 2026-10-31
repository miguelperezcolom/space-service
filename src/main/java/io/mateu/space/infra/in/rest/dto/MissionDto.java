package io.mateu.space.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A mission on the wire. References travel as ids plus, for the single-valued ones, the display
 * name ({@code destinationId} + {@code destinationName}) so a table needs no second request.
 *
 * <p>On a write: the scalar fields are replaced; {@code crewIds}, {@code gallery} and the picture
 * fields are kept as they are when the body leaves them out (null) — a form that edits the facts
 * should not wipe the photographs. Names, {@code url}, {@code wikidataUrl} are read-only. The cost
 * travels twice: {@code costUsd} (a number, what a table and a range filter read) and {@code cost}
 * (a money value, what a money widget reads); a write takes {@code cost} when it is given.
 */
public record MissionDto(
    Long id,
    @NotBlank(message = "must not be blank") String name,
    String program,
    String agency,
    String type,
    String status,
    LocalDate launchDate,
    LocalDate endDate,
    Integer launchYear,
    /** The launch window, as {from, to}; written leniently (see {@link DateWindow}). */
    DateWindow launchWindow,
    Long destinationId,
    String destinationName,
    Long launchSiteId,
    String launchSiteName,
    Long spacecraftId,
    String spacecraftName,
    IdList crewIds,
    List<String> crewNames,
    Integer crewSize,
    BigDecimal costUsd,
    /** The same cost as a money value {value, currency}; on a write it wins over costUsd. */
    Amount cost,
    String summary,
    String objectives,
    Boolean featured,
    String wikidataId,
    String wikidataUrl,
    String url,
    String image,
    String imageLarge,
    String thumbnail,
    String imageTitle,
    String imageCredit,
    String sourceUrl,
    List<PictureDto> gallery) {}
