package io.mateu.space.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;

/**
 * An empty string in a body means "no value", for every field type.
 *
 * <p>A declarative client writes its body by interpolating a JSON template — {@code
 * {"costUsd":"${state.costUsd}"}} — so a field the user left empty arrives as {@code ""}, numbers
 * and dates included. Reading that as null is what the user meant; refusing it would make every
 * optional number or date impossible to leave blank from such a client.
 */
@Configuration
public class JacksonConfig {

  @Bean
  JsonMapperBuilderCustomizer emptyStringIsNull() {
    return builder -> builder.withCoercionConfigDefaults(
        config -> config.setCoercion(CoercionInputShape.EmptyString, CoercionAction.AsNull));
  }
}
