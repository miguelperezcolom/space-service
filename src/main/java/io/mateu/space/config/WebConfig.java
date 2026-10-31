package io.mateu.space.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS, wide open for reads and writes alike.
 *
 * <p>A browser is the primary client here — the whole point is that a static bundle with no backend
 * of its own can talk to this directly — so the API answers any origin. Note the write key travels
 * in a CUSTOM header, and a custom header is exactly what makes a request "non-simple": without it
 * being named in the allowed headers the browser refuses the preflight and every write fails with a
 * CORS error that says nothing about the key.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final String writeKeyHeader;

  public WebConfig(@Value("${space.write-key-header}") String writeKeyHeader) {
    this.writeKeyHeader = writeKeyHeader;
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOriginPatterns("*")
        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        .allowedHeaders("Content-Type", "Accept", writeKeyHeader)
        .exposedHeaders("Location")
        .allowCredentials(false)
        .maxAge(3600);
  }
}
