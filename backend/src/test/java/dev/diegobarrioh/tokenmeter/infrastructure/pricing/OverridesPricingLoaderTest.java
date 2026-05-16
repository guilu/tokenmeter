package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingConfigurationException;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class OverridesPricingLoaderTest {

  @Test
  void missingResourceProducesEmptyList() {
    Resource missing =
        new ByteArrayResource(new byte[0]) {
          @Override
          public boolean exists() {
            return false;
          }
        };
    ResourceLoader missingLoader = staticLoader(missing);

    OverridesPricingLoader loader = new OverridesPricingLoader(missingLoader, properties());

    assertThat(loader.snapshots()).isEmpty();
  }

  @Test
  void emptyFileProducesEmptyList() {
    OverridesPricingLoader loader =
        new OverridesPricingLoader(resourceLoader("overrides: []\n"), properties());

    assertThat(loader.snapshots()).isEmpty();
  }

  @Test
  void loadsValidEntriesTaggedAsOverride() {
    OverridesPricingLoader loader =
        new OverridesPricingLoader(
            resourceLoader(
                """
                overrides:
                  - provider: anthropic
                    model: claude-opus-4-7
                    input-token-price: 12.00
                    output-token-price: 60.00
                  - provider: openai
                    model: gpt-4o
                    input-token-price: 1.00
                    output-token-price: 4.00
                """),
            properties());

    List<PricingSnapshot> snapshots = loader.snapshots();

    assertThat(snapshots).hasSize(2);
    assertThat(snapshots)
        .allSatisfy(
            snapshot -> {
              assertThat(snapshot.source()).isEqualTo(PricingSource.OVERRIDE);
              assertThat(snapshot.fetchedAt()).isNotNull();
              assertThat(snapshot.externalModelId()).isNull();
            });
    PricingSnapshot anthropic = snapshots.get(0);
    assertThat(anthropic.provider()).isEqualTo(AiProvider.ANTHROPIC);
    assertThat(anthropic.model()).isEqualTo("claude-opus-4-7");
    assertThat(anthropic.pricing().inputTokenPricePerMillion()).isEqualByComparingTo("12.00");
    assertThat(anthropic.pricing().outputTokenPricePerMillion()).isEqualByComparingTo("60.00");
  }

  @Test
  void rejectsDuplicateEntry() {
    assertThatThrownBy(
            () ->
                new OverridesPricingLoader(
                    resourceLoader(
                        """
                        overrides:
                          - provider: openai
                            model: gpt-4o
                            input-token-price: 1.00
                            output-token-price: 4.00
                          - provider: openai
                            model: gpt-4o
                            input-token-price: 2.00
                            output-token-price: 8.00
                        """),
                    properties()))
        .isInstanceOf(PricingConfigurationException.class)
        .hasMessageContaining("duplicated override entry");
  }

  @Test
  void rejectsUnknownProvider() {
    assertThatThrownBy(
            () ->
                new OverridesPricingLoader(
                    resourceLoader(
                        """
                        overrides:
                          - provider: imaginary
                            model: foo
                            input-token-price: 1.00
                            output-token-price: 2.00
                        """),
                    properties()))
        .isInstanceOf(PricingConfigurationException.class)
        .hasMessageContaining("unknown provider");
  }

  @Test
  void rejectsNegativePrice() {
    assertThatThrownBy(
            () ->
                new OverridesPricingLoader(
                    resourceLoader(
                        """
                        overrides:
                          - provider: openai
                            model: gpt-4o
                            input-token-price: -1.00
                            output-token-price: 2.00
                        """),
                    properties()))
        .isInstanceOf(PricingConfigurationException.class)
        .hasMessageContaining("invalid override entry");
  }

  private static ResourceLoader resourceLoader(String yaml) {
    Resource resource =
        new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)) {
          @Override
          public boolean exists() {
            return true;
          }
        };
    return staticLoader(resource);
  }

  private static ResourceLoader staticLoader(Resource resource) {
    return new ResourceLoader() {
      @Override
      public Resource getResource(String location) {
        return resource;
      }

      @Override
      public ClassLoader getClassLoader() {
        return OverridesPricingLoaderTest.class.getClassLoader();
      }
    };
  }

  private static PricingProperties properties() {
    return new PricingProperties(
        "classpath:pricing.yaml",
        "classpath:pricing-mapping.yaml",
        "classpath:pricing-overrides.yaml",
        null,
        null,
        null);
  }
}
