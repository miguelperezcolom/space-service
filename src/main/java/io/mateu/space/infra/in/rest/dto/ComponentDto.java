package io.mateu.space.infra.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * One node of a spacecraft's breakdown. In the flat collection {@code children} is null; in a tree
 * answer ({@code /api/spacecraft/tree}, {@code /api/spacecraft/{id}/components/tree}) it holds the
 * node's children, which is the shape a tree grid reads.
 */
public record ComponentDto(
    Long id,
    @NotBlank(message = "must not be blank") String name,
    String kind,
    String description,
    Long spacecraftId,
    String spacecraftName,
    Long parentId,
    String parentName,
    String url,
    List<ComponentDto> children) {}
