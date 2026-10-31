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

  /**
   * A field the body leaves out (null) keeps its value: a form editing facts must not wipe photos.
   * A new {@code image} without its sizes brings them along — derived from it ({@link #size}) — so a
   * form that only changes the photo does not leave the old thumbnail and large version behind.
   */
  static Picture merge(Picture current, String image, String imageLarge, String thumbnail,
      String imageTitle, String imageCredit, String sourceUrl) {
    Picture p = current == null ? new Picture() : current;
    boolean newImage = image != null && !java.util.Objects.equals(blankToNull(image), p.getImage());
    if (image != null) p.setImage(blankToNull(image));
    if (newImage) {
      if (imageLarge == null) p.setImageLarge(size(p.getImage(), "large"));
      if (thumbnail == null) p.setThumbnail(size(p.getImage(), "thumb"));
    }
    if (imageLarge != null) p.setImageLarge(blankToNull(imageLarge));
    if (thumbnail != null) p.setThumbnail(blankToNull(thumbnail));
    if (imageTitle != null) p.setImageTitle(blankToNull(imageTitle));
    if (imageCredit != null) p.setImageCredit(blankToNull(imageCredit));
    if (sourceUrl != null) p.setSourceUrl(blankToNull(sourceUrl));
    return p;
  }

  private static final java.util.regex.Pattern NASA_SIZE =
      java.util.regex.Pattern.compile("~(orig|large|medium|small|thumb)(\\.[a-z]+)$");

  /**
   * The same picture at another size: the NASA Image Library names its renditions {@code
   * ~thumb/~small/~medium/~large/~orig}; any other URL is its own only size.
   */
  static String size(String url, String size) {
    if (url == null) return null;
    var m = NASA_SIZE.matcher(url);
    return m.find() ? m.replaceFirst("~" + size + "$2") : url;
  }

  private static String blankToNull(String s) {
    return s.isBlank() ? null : s;
  }
}
