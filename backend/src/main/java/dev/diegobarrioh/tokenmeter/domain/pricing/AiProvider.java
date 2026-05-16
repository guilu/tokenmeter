package dev.diegobarrioh.tokenmeter.domain.pricing;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AiProvider {
  OPENAI,
  ANTHROPIC,
  GOOGLE,
  DEEPSEEK,
  MISTRAL,
  ALIBABA,
  XAI;

  public String configKey() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Optional<AiProvider> fromConfigKey(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    return Arrays.stream(values())
        .filter(provider -> provider.name().equals(normalized))
        .findFirst();
  }
}
