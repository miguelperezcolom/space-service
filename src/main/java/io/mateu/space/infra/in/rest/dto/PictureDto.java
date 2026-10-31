package io.mateu.space.infra.in.rest.dto;

/** One photograph of a mission's gallery (see {@link io.mateu.space.domain.Picture}). */
public record PictureDto(
    String image,
    String imageLarge,
    String thumbnail,
    String imageTitle,
    String imageCredit,
    String sourceUrl) {}
