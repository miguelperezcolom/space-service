package io.mateu.space.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** A mission task — a card on the board. */
public record TaskDto(
    Long id,
    @NotBlank(message = "must not be blank") String title,
    Long missionId,
    String missionName,
    String phase,
    String status,
    String owner,
    String priority,
    LocalDate dueDate,
    Integer estimateDays,
    Integer progress,
    String description,
    String url) {}
