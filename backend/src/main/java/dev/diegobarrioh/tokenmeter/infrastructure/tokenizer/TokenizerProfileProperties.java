package dev.diegobarrioh.tokenmeter.infrastructure.tokenizer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration block bound to {@code tokenmeter.tokenizer.*}.
 *
 * <p>{@link #profilesLocation()} defaults to {@code classpath:tokenizer-profiles.yaml} and can be
 * overridden to point to an external file (e.g. for testing with custom profiles or for
 * multi-environment deployments).
 */
@ConfigurationProperties("tokenmeter.tokenizer")
public record TokenizerProfileProperties(String profilesLocation) {

  public TokenizerProfileProperties {
    if (profilesLocation == null || profilesLocation.isBlank()) {
      profilesLocation = "classpath:tokenizer-profiles.yaml";
    }
  }
}
