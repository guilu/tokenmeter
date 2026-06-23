package dev.diegobarrioh.tokenmeter.application.tokenizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenCounterStrategy;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenizationPrecision;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelTokenizationProfileResolverTest {

  private ModelTokenizationProfileResolver resolver;

  @BeforeEach
  void setUp() {
    // Load the real production tokenizer-profiles.yaml via TokenizerProfileLoader
    org.springframework.core.io.DefaultResourceLoader resourceLoader =
        new org.springframework.core.io.DefaultResourceLoader();
    dev.diegobarrioh.tokenmeter.infrastructure.tokenizer.TokenizerProfileLoader loader =
        new dev.diegobarrioh.tokenmeter.infrastructure.tokenizer.TokenizerProfileLoader(
            resourceLoader,
            new dev.diegobarrioh.tokenmeter.infrastructure.tokenizer.TokenizerProfileProperties(
                null));
    resolver = new ModelTokenizationProfileResolver(loader);
  }

  @Test
  void openAiGpt4oMiniResolvesToO200kExactLocal() {
    ModelTokenizationProfile profile = resolver.resolve(AiProvider.OPENAI, "gpt-4o-mini");

    assertThat(profile.tokenizerId()).isEqualTo("openai/o200k_base");
    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.EXACT_LOCAL);
    assertThat(profile.strategy()).isEqualTo(TokenCounterStrategy.JTOKKIT);
  }

  @Test
  void openAiGpt4TurboResolvesToCl100kExactLocal() {
    ModelTokenizationProfile profile = resolver.resolve(AiProvider.OPENAI, "gpt-4-turbo");

    assertThat(profile.tokenizerId()).isEqualTo("openai/cl100k_base");
    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.EXACT_LOCAL);
  }

  @Test
  void anthropicModelResolvesToHeuristic() {
    ModelTokenizationProfile profile =
        resolver.resolve(AiProvider.ANTHROPIC, "claude-3-5-sonnet-20241022");

    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.HEURISTIC);
    assertThat(profile.strategy()).isEqualTo(TokenCounterStrategy.HEURISTIC);
  }

  @Test
  void googleGeminiResolvesToHeuristic() {
    ModelTokenizationProfile profile = resolver.resolve(AiProvider.GOOGLE, "gemini-2.5-pro");

    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.HEURISTIC);
  }

  @Test
  void unknownModelNeverThrowsReturnsDefault() {
    assertThatCode(() -> resolver.resolve(AiProvider.OPENAI, "totally-unknown-xyz"))
        .doesNotThrowAnyException();

    ModelTokenizationProfile profile = resolver.resolve(AiProvider.OPENAI, "totally-unknown-xyz");
    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.HEURISTIC);
  }

  @Test
  void nullProviderNeverThrowsReturnsDefault() {
    assertThatCode(() -> resolver.resolve(null, null)).doesNotThrowAnyException();

    ModelTokenizationProfile profile = resolver.resolve(null, null);
    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.HEURISTIC);
  }

  @Test
  void distinctTokenizersDeduplicatesByTokenizerId() {
    // Two OpenAI gpt-4o models → same tokenizerId openai/o200k_base
    // One Anthropic model → anthropic/cl100k_heuristic
    List<PricingSnapshot> snapshots =
        List.of(
            snapshot(AiProvider.OPENAI, "gpt-4o"),
            snapshot(AiProvider.OPENAI, "gpt-4o-mini"),
            snapshot(AiProvider.ANTHROPIC, "claude-3-5-sonnet-20241022"));

    Map<String, ModelTokenizationProfile> distinct = resolver.distinctTokenizers(snapshots);

    // gpt-4o and gpt-4o-mini both map to openai/o200k_base → deduplicated to 1
    // anthropic → 1
    assertThat(distinct).hasSize(2);
    assertThat(distinct).containsKey("openai/o200k_base");
    assertThat(distinct).containsKey("anthropic/cl100k_heuristic");
  }

  private static PricingSnapshot snapshot(AiProvider provider, String model) {
    ModelPricing pricing = new ModelPricing(provider, model, BigDecimal.ONE, BigDecimal.ONE);
    return new PricingSnapshot(pricing, PricingSource.FALLBACK, OffsetDateTime.now(), null);
  }
}
