package io.mateu.space.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A photograph from the NASA Image and Video Library, linked where NASA serves it
 * (images-assets.nasa.gov) — this service stores the addresses, never the files. The credit is the
 * asset's own (its secondary creator, photographer or NASA centre), and the source is its page on
 * images.nasa.gov, so every picture can be traced back to where it was published.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Picture {

  /** The medium rendition (~medium.jpg): what a page shows. */
  @Column(length = 1000)
  private String image;

  /** The large rendition (~large.jpg, or the original when there is none): for a full view. */
  @Column(length = 1000)
  private String imageLarge;

  /** The thumbnail (~thumb.jpg): for a list or a card. */
  @Column(length = 1000)
  private String thumbnail;

  /** The asset's title on images.nasa.gov — a caption, and the image's alternative text. */
  @Column(length = 500)
  private String imageTitle;

  /** Who to credit, e.g. "NASA/JPL-Caltech". */
  @Column(length = 500)
  private String imageCredit;

  /** The asset's page on images.nasa.gov. */
  @Column(length = 1000)
  private String sourceUrl;
}
