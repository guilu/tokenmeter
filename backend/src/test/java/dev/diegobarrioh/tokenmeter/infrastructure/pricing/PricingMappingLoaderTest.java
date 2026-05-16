package dev.diegobarrioh.tokenmeter.infrastructure.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.application.pricing.PricingConfigurationException;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.PricingMappingLoader.MappingKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class PricingMappingLoaderTest {

  @Test
  void loadsConfiguredEntriesAndNormalizesModelNames() {
    PricingMappingLoader loader =
        new PricingMappingLoader(
            resourceLoader(
                """
                mapping:
                  - provider: openai
                    model: GPT-4O
                    litellm-key: gpt-4o
                  - provider: anthropic
                    model: claude-opus-4-7
                    litellm-key: claude-opus-4-7
                  - provider: deepseek
                    model: deepseek-chat
                    litellm-key: deepseek/deepseek-chat
                """),
            properties("classpath:mapping.yaml"));

    Map<MappingKey, String> mappings = loader.mappings();

    assertThat(mappings).hasSize(3);
    assertThat(mappings).containsEntry(new MappingKey(AiProvider.OPENAI, "gpt-4o"), "gpt-4o");
    assertThat(mappings)
        .containsEntry(new MappingKey(AiProvider.ANTHROPIC, "claude-opus-4-7"), "claude-opus-4-7");
    assertThat(mappings)
        .containsEntry(
            new MappingKey(AiProvider.DEEPSEEK, "deepseek-chat"), "deepseek/deepseek-chat");
  }

  @Test
  void mappingsAreImmutable() {
    PricingMappingLoader loader =
        new PricingMappingLoader(
            resourceLoader(
                """
                mapping:
                  - provider: openai
                    model: gpt-4o
                    litellm-key: gpt-4o
                """),
            properties("classpath:mapping.yaml"));

    Map<MappingKey, String> mappings = loader.mappings();

    assertThatThrownBy(() -> mappings.put(new MappingKey(AiProvider.OPENAI, "x"), "x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void reverseLookupExposesUpstreamKeys() {
    PricingMappingLoader loader =
        new PricingMappingLoader(
            resourceLoader(
                """
                mapping:
                  - provider: openai
                    model: gpt-4o
                    litellm-key: gpt-4o
                """),
            properties("classpath:mapping.yaml"));

    assertThat(loader.reverseLookup("gpt-4o"))
        .contains(new MappingKey(AiProvider.OPENAI, "gpt-4o"));
    assertThat(loader.reverseLookup("missing-key")).isEmpty();
    assertThat(loader.reverseLookup(null)).isEmpty();
    assertThat(loader.reverseLookup("")).isEmpty();
  }

  @Test
  void missingResourceRaisesExplicitError() {
    Resource missingResource =
        new ByteArrayResource(new byte[0]) {
          @Override
          public boolean exists() {
            return false;
          }
        };
    ResourceLoader missingLoader = staticLoader(missingResource);

    assertThatThrownBy(
            () -> new PricingMappingLoader(missingLoader, properties("classpath:missing.yaml")))
        .isInstanceOf(PricingConfigurationException.class)
        .hasMessageContaining("pricing-mapping.yaml not found");
  }

  @Test
  void rejectsUnknownProvider() {
    ResourceLoader resourceLoader =
        resourceLoader(
            """
            mapping:
              - provider: imaginary
                model: foo
                litellm-key: foo
            """);

    assertThatThrownBy(
            () -> new PricingMappingLoader(resourceLoader, properties("classpath:mapping.yaml")))
        .isInstanceOf(PricingConfigurationException.class)
        .hasMessageContaining("unknown provider");
  }

  @Test
  void rejectsBlankLitellmKey() {
    ResourceLoader resourceLoader =
        resourceLoader(
            """
            mapping:
              - provider: openai
                model: gpt-4o
                litellm-key: ""
            """);

    assertThatThrownBy(
            () -> new PricingMappingLoader(resourceLoader, properties("classpath:mapping.yaml")))
        .isInstanceOf(PricingConfigurationException.class)
        .hasMessageContaining("litellm-key is required");
  }

  @Test
  void rejectsDuplicatedEntry() {
    ResourceLoader resourceLoader =
        resourceLoader(
            """
            mapping:
              - provider: openai
                model: gpt-4o
                litellm-key: gpt-4o
              - provider: openai
                model: GPT-4O
                litellm-key: gpt-4o-other
            """);

    assertThatThrownBy(
            () -> new PricingMappingLoader(resourceLoader, properties("classpath:mapping.yaml")))
        .isInstanceOf(PricingConfigurationException.class)
        .hasMessageContaining("duplicated mapping entry");
  }

  @Test
  void rejectsEmptyMappingList() {
    ResourceLoader resourceLoader =
        resourceLoader(
            """
            mapping: []
            """);

    assertThatThrownBy(
            () -> new PricingMappingLoader(resourceLoader, properties("classpath:mapping.yaml")))
        .isInstanceOf(PricingConfigurationException.class)
        .hasMessageContaining("must contain at least one entry");
  }

  @Test
  void productionMappingFileLoadsSeventeenEntries() {
    ResourceLoader productionLoader = new org.springframework.core.io.DefaultResourceLoader();
    PricingMappingLoader loader =
        new PricingMappingLoader(productionLoader, properties("classpath:pricing-mapping.yaml"));

    assertThat(loader.mappings()).hasSize(17);
  }

  private static ResourceLoader resourceLoader(String yaml) {
    byte[] bytes = yaml.getBytes(StandardCharsets.UTF_8);
    Resource resource =
        new ByteArrayResource(bytes) {
          @Override
          public boolean exists() {
            return true;
          }

          @Override
          public InputStream getInputStream() throws IOException {
            return super.getInputStream();
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
        return PricingMappingLoaderTest.class.getClassLoader();
      }
    };
  }

  private static PricingProperties properties(String mappingLocation) {
    return new PricingProperties(
        "classpath:pricing.yaml",
        mappingLocation,
        "classpath:pricing-overrides.yaml",
        null,
        null,
        null);
  }
}
