package io.mateu.space;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A writable space-exploration API: NASA missions, astronauts, spacecraft (with their component
 * trees), launch sites, launches, celestial bodies, mission tasks and budget lines, with search,
 * filters, paging, writes and real references between tables, over an in-memory H2 seeded at boot
 * from a curated Wikidata + NASA Image and Video Library snapshot (see scripts/build-seed.py).
 *
 * <p>The images are linked where NASA publishes them; nothing is fetched at runtime.
 */
@SpringBootApplication
public class SpaceApplication {

  public static void main(String[] args) {
    SpringApplication.run(SpaceApplication.class, args);
  }
}
