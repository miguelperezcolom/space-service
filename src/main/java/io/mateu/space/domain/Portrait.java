package io.mateu.space.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An astronaut's portrait UPLOADED to this service (the seeded ones are linked from NASA, see
 * {@link Picture}). Kept in the database like everything else, so it is gone after a restart, as
 * every other change to the demo is. One per astronaut, keyed by its id; {@code version} changes
 * with every upload so the URL that names it does too and a browser never shows a stale copy.
 */
@Entity
@Table(name = "astronaut_portrait")
@Getter
@Setter
@NoArgsConstructor
public class Portrait {

  @Id private Long astronautId;

  @Column(length = 40, nullable = false)
  private String contentType;

  @Column(length = 32, nullable = false)
  private String version;

  @Lob
  @Column(nullable = false)
  private byte[] bytes;
}
