package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

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
    String key = litellmKey.trim().toLowerCase(Locale.ROOT);
    if (key.startsWith("ft:")) {
      return false;
    }
    String model = key.substring(key.lastIndexOf('/') + 1);
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
}
