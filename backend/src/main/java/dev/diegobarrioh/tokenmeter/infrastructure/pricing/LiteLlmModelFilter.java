package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Decides whether an upstream LiteLLM key represents a canonical/active text model worth importing
 * (TKM-66). Filters out the variant noise the full-catalogue import surfaces: dated snapshots,
 * fine-tuned models, non-text modalities (audio/image/tts/realtime/transcribe/search/vision/…),
 * {@code -latest} aliases and preview/beta/experimental builds.
 *
 * <p>Pure, stateless, deterministic. Applied ONLY to auto-discovered entries — entries configured
 * in {@code pricing-mapping.yaml} bypass this filter, so an operator can force a specific
 * preview/variant back in via an explicit override.
 */
final class LiteLlmModelFilter {

  /**
   * Trailing ISO date ({@code -2025-08-07}) or a {@code -NNN}+ snapshot code ({@code -001}, -2407).
   */
  private static final Pattern DATED_SUFFIX =
      Pattern.compile(".*-\\d{4}-\\d{2}-\\d{2}$|.*-\\d{3,}$");

  /** Non-text modalities — TokenMeter only estimates code/text regeneration cost. */
  private static final List<String> MODALITY_MARKERS =
      List.of(
          "audio",
          "realtime",
          "transcribe",
          "tts",
          "image",
          "search",
          "research",
          "vision",
          "computer-use",
          "robotics",
          "native-audio",
          "embed",
          "moderation",
          "rerank",
          "whisper",
          "dall-e",
          "live");

  /** Rolling aliases and non-stable builds. */
  private static final List<String> ALIAS_MARKERS = List.of("latest", "preview", "beta", "exp");

  private LiteLlmModelFilter() {}

  static boolean isCanonical(String litellmKey) {
    if (litellmKey == null || litellmKey.isBlank()) {
      return false;
    }
    String model = modelPortion(litellmKey);
    if (litellmKey.trim().toLowerCase(Locale.ROOT).startsWith("ft:")) {
      return false;
    }
    if (DATED_SUFFIX.matcher(model).matches()) {
      return false;
    }
    for (String marker : MODALITY_MARKERS) {
      if (model.contains(marker)) {
        return false;
      }
    }
    for (String marker : ALIAS_MARKERS) {
      if (model.contains(marker)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Provider-aware variant (TKM-69): in addition to the generic noise rules, drops
   * provider-specific tiers that distort cost rankings — currently OpenAI {@code -pro} models
   * (gpt-5-pro, o1-pro, …), whose extreme pricing does not reflect normal usage.
   */
  static boolean isCanonical(AiProvider provider, String litellmKey) {
    return isCanonical(litellmKey) && !isProviderExcludedTier(provider, litellmKey);
  }

  private static boolean isProviderExcludedTier(AiProvider provider, String litellmKey) {
    if (provider != AiProvider.OPENAI) {
      return false;
    }
    String model = modelPortion(litellmKey);
    return model.endsWith("-pro") || model.contains("-pro-");
  }

  /** The model identifier with any {@code provider/} prefix stripped, trimmed and lower-cased. */
  private static String modelPortion(String litellmKey) {
    String key = litellmKey.trim().toLowerCase(Locale.ROOT);
    return key.substring(key.lastIndexOf('/') + 1);
  }
}
