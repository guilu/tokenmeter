package dev.diegobarrioh.tokenmeter.domain.pricing;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum AiProvider {
  OPENAI,
  ANTHROPIC,
  GOOGLE,
  DEEPSEEK,
  MISTRAL,
  ALIBABA,
  XAI;

  /**
   * Allowlist of LiteLLM {@code litellm_provider} tokens that TokenMeter auto-imports (TKM-65).
   * Only unambiguous, canonical tokens are listed — ambiguous variants (e.g. {@code vertex_ai-*},
   * {@code qwen}) are intentionally excluded and enter solely via explicit {@code
   * pricing-mapping.yaml} overrides, so automatic discovery never pulls noisy/duplicate rows.
   */
  private static final Map<String, AiProvider> LITELLM_PROVIDER_ALIASES =
      Map.of(
          "openai", OPENAI,
          "anthropic", ANTHROPIC,
          "gemini", GOOGLE,
          "deepseek", DEEPSEEK,
          "mistral", MISTRAL,
          "xai", XAI);

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

  /**
   * Resolves a supported {@link AiProvider} from a LiteLLM {@code litellm_provider} token, used to
   * auto-discover models absent from {@code pricing-mapping.yaml}. Returns {@link Optional#empty()}
   * for blank input or any token outside the curated {@link #LITELLM_PROVIDER_ALIASES} allowlist.
   */
  public static Optional<AiProvider> fromLiteLlmProvider(String litellmProvider) {
    if (litellmProvider == null || litellmProvider.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(
        LITELLM_PROVIDER_ALIASES.get(litellmProvider.trim().toLowerCase(Locale.ROOT)));
  }
}
