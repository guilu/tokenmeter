package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = "tokenmeter.pricing.overrides-location=classpath:fixtures/pricing-overrides.yaml")
class CompositePricingProviderIntegrationTest {

  @Autowired private PricingProvider pricingProvider;

  @Test
  void overrideRowsShadowFallbackRowsWhenOverridesFileIsPresent() {
    var snapshots = pricingProvider.snapshots();
    assertThat(snapshots).isNotEmpty();

    PricingSnapshot opus =
        snapshots.stream()
            .filter(
                s -> s.provider() == AiProvider.ANTHROPIC && s.model().equals("claude-opus-4-7"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("claude-opus-4-7 missing"));
    assertThat(opus.source()).isEqualTo(PricingSource.OVERRIDE);
    assertThat(opus.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("12.00");
    assertThat(opus.pricing().outputTokenPricePerMillion()).isEqualByComparingTo("60.00");

    PricingSnapshot gpt4o =
        snapshots.stream()
            .filter(s -> s.provider() == AiProvider.OPENAI && s.model().equals("gpt-4o"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("gpt-4o missing"));
    assertThat(gpt4o.source()).isEqualTo(PricingSource.OVERRIDE);
    assertThat(gpt4o.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("1.50");
    assertThat(gpt4o.pricing().outputTokenPricePerMillion()).isEqualByComparingTo("6.00");

    PricingSnapshot mistral =
        snapshots.stream()
            .filter(s -> s.provider() == AiProvider.MISTRAL && s.model().equals("codestral"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("codestral missing"));
    assertThat(mistral.source()).isEqualTo(PricingSource.FALLBACK);
  }
}
