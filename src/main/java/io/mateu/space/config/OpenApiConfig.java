package io.mateu.space.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The published contract: /v3/api-docs, rendered at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI spaceOpenApi(@Value("${space.write-key-header}") String writeKeyHeader) {
    return new OpenAPI()
        .info(
            new Info()
                .title("Space service")
                .version("0.1.0")
                .description(
                    """
                    A writable space-exploration API — missions, astronauts, spacecraft and their \
                    components, launch sites, launches, celestial bodies, mission tasks and budget \
                    lines — over an in-memory H2, seeded at boot from a curated snapshot of Wikidata \
                    (CC0) and the NASA Image and Video Library.

                    **Reads are open. Writes need the `%s` header.**

                    Every collection answers `GET /api/<collection>` with a paged envelope \
                    (`content` + `totalElements`) and supports `search`, its own declared filters, \
                    `<field>_from`/`<field>_to` ranges, `sort` and `page`/`size`. A filter the \
                    collection does not declare is a 400, never a silently unfiltered page. Trees \
                    (`/api/bodies/tree`, `/api/spacecraft/tree`) answer the same envelope, unpaged, \
                    with `children` on each node. `/api/search` searches every collection at once.

                    Photographs are linked from images-assets.nasa.gov, each with its credit and \
                    source page (`/api/credits`). NASA media is generally not copyrighted, but NASA \
                    asks for credit and that its insignia not imply endorsement: the seed carries \
                    photographs, not logos. Mission tasks and the budget split are synthetic.

                    The database is in memory and re-seeded on every restart, so anything written \
                    here is demo data with a short life.
                    """
                        .formatted(writeKeyHeader))
                .license(new License().name("MIT")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "writeKey",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(writeKeyHeader)
                        .description("Required on POST, PUT, PATCH and DELETE.")));
  }
}
