package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LiteLlmModelFilterTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "claude-opus-4-8",
        "claude-fable-5",
        "gpt-5.2",
        "gpt-4o",
        "gpt-4o-mini",
        "gpt-4-turbo",
        "o3",
        "o4-mini",
        "deepseek/deepseek-chat",
        "deepseek-r1",
        "gemini/gemini-2.5-pro",
        "grok-4",
        "grok-code-fast-1",
        "mistral-large-3",
        "codestral"
      })
  void keepsCanonicalModels(String key) {
    assertThat(LiteLlmModelFilter.isCanonical(key)).as(key).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // dated snapshots
        "claude-opus-4-7-20260416",
        "gpt-5-2025-08-07",
        "gpt-4.1-2025-04-14",
        "mistral-large-2407",
        "grok-2-1212",
        "claude-3-haiku-20240307",
        "gpt-4-0314",
        "gemini-2.0-flash-001",
        // fine-tuned
        "ft:gpt-4o-2024-08-06",
        "ft:gpt-3.5-turbo",
        // non-text modalities
        "gpt-4o-audio-preview",
        "gpt-4o-realtime-preview",
        "gpt-4o-transcribe",
        "gpt-4o-mini-tts",
        "gpt-image-2",
        "gpt-4o-search-preview",
        "grok-2-vision",
        "o3-deep-research",
        "gemini-2.5-computer-use-preview-10-2025",
        "gemini-robotics-er-1.5-preview",
        "gemini-2.5-flash-native-audio-latest",
        // -latest aliases + preview/beta/exp
        "grok-4-latest",
        "mistral-large-latest",
        "gemini-flash-latest",
        "gpt-5-chat-latest",
        "gemini-3-pro-preview",
        "grok-3-beta",
        "gemini-exp-1206"
      })
  void dropsNoiseVariants(String key) {
    assertThat(LiteLlmModelFilter.isCanonical(key)).as(key).isFalse();
  }

  @Test
  void treatsBlankInputAsNonCanonical() {
    assertThat(LiteLlmModelFilter.isCanonical(null)).isFalse();
    assertThat(LiteLlmModelFilter.isCanonical("  ")).isFalse();
  }
}
