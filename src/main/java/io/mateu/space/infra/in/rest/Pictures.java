package io.mateu.space.infra.in.rest;

import io.mateu.space.domain.Picture;
import io.mateu.space.infra.in.rest.dto.PictureDto;

/** Copies the six picture fields between an entity's {@link Picture} and a DTO. */
final class Pictures {

  private Pictures() {}

  static PictureDto dto(Picture p) {
    return p == null
        ? null
        : new PictureDto(p.getImage(), p.getImageLarge(), p.getThumbnail(), p.getImageTitle(),
            p.getImageCredit(), p.getSourceUrl());
  }

  static Picture entity(PictureDto d) {
    return new Picture(d.image(), d.imageLarge(), d.thumbnail(), d.imageTitle(), d.imageCredit(),
        d.sourceUrl());
  }

  /** A field the body leaves out (null) keeps its value: a form editing facts must not wipe photos. */
  static Picture merge(Picture current, String image, String imageLarge, String thumbnail,
      String imageTitle, String imageCredit, String sourceUrl) {
    Picture p = current == null ? new Picture() : current;
    if (image != null) p.setImage(blankToNull(image));
    if (imageLarge != null) p.setImageLarge(blankToNull(imageLarge));
    if (thumbnail != null) p.setThumbnail(blankToNull(thumbnail));
    if (imageTitle != null) p.setImageTitle(blankToNull(imageTitle));
    if (imageCredit != null) p.setImageCredit(blankToNull(imageCredit));
    if (sourceUrl != null) p.setSourceUrl(blankToNull(sourceUrl));
    return p;
  }

  private static String blankToNull(String s) {
    return s.isBlank() ? null : s;
  }
}
