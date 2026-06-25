package dev.diegobarrioh.tokenmeter.infrastructure.tokenizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenCounterStrategy;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenizationPrecision;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class TokenizerProfileLoaderTest {

  @Test
  void loadsProductionYamlSuccessfully() {
    TokenizerProfileLoader loader =
        new TokenizerProfileLoader(
            new DefaultResourceLoader(), new TokenizerProfileProperties(null));

    // Verify it loads without exception and the DEFAULT profile is not null
    ModelTokenizationProfile defaultProfile = loader.defaultProfile();
    assertThat(defaultProfile).isNotNull();
    assertThat(defaultProfile.tokenizerId()).isNotBlank();
  }

  @Test
  void openAiO200kEntryLoadedCorrectly() {
    TokenizerProfileLoader loader =
        new TokenizerProfileLoader(
            new DefaultResourceLoader(), new TokenizerProfileProperties(null));

    // resolve gpt-4o → should match openai/o200k_base
    ModelTokenizationProfile profile =
        loader.resolve(dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider.OPENAI, "gpt-4o");

    assertThat(profile.tokenizerId()).isEqualTo("openai/o200k_base");
    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.EXACT_LOCAL);
    assertThat(profile.strategy()).isEqualTo(TokenCounterStrategy.JTOKKIT);
    assertThat(profile.encoding()).isEqualTo("O200K_BASE");
  }

  @Test
  void anthropicHeuristicEntryLoadedCorrectly() {
    TokenizerProfileLoader loader =
        new TokenizerProfileLoader(
            new DefaultResourceLoader(), new TokenizerProfileProperties(null));

    ModelTokenizationProfile profile =
        loader.resolve(
            dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider.ANTHROPIC,
            "claude-3-5-sonnet-20241022");

    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.HEURISTIC);
    assertThat(profile.strategy()).isEqualTo(TokenCounterStrategy.HEURISTIC);
    assertThat(profile.heuristicFactor()).isNotNull();
    assertThat(profile.heuristicFactor().signum()).isPositive();
  }

  @Test
  void defaultProfileIsNotNull() {
    TokenizerProfileLoader loader =
        new TokenizerProfileLoader(
            new DefaultResourceLoader(), new TokenizerProfileProperties(null));

    ModelTokenizationProfile defaultProfile = loader.defaultProfile();

    assertThat(defaultProfile).isNotNull();
    assertThat(defaultProfile.tokenizerId()).isNotBlank();
  }

  @Test
  void unknownModelReturnsDefaultProfile() {
    TokenizerProfileLoader loader =
        new TokenizerProfileLoader(
            new DefaultResourceLoader(), new TokenizerProfileProperties(null));

    ModelTokenizationProfile profile =
        loader.resolve(
            dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider.OPENAI,
            "completely-unknown-model-xyz-999");

    assertThat(profile).isEqualTo(loader.defaultProfile());
  }

  @Test
  void missingYamlThrowsAtConstruction() {
    ResourceLoader missingLoader = staticLoader(missingResource());

    assertThatThrownBy(
            () -> new TokenizerProfileLoader(missingLoader, new TokenizerProfileProperties(null)))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void invalidEncodingNameThrowsAtConstruction() {
    String yaml =
        """
        profiles:
          - provider: openai
            model-pattern: "gpt-4.*"
            tokenizer-id: "openai/o200k_base"
            precision: EXACT_LOCAL
            strategy: JTOKKIT
            encoding: INVALID_ENCODING_NAME_XYZ
        default:
          tokenizer-id: "openai/cl100k_base"
          precision: HEURISTIC
          strategy: JTOKKIT
          encoding: CL100K_BASE
        """;
    ResourceLoader loader = inMemoryLoader(yaml);

    assertThatThrownBy(
            () -> new TokenizerProfileLoader(loader, new TokenizerProfileProperties(null)))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void heuristicEntryWithZeroFactorThrowsAtConstruction() {
    String yaml =
        """
        profiles:
          - provider: anthropic
            model-pattern: ".*"
            tokenizer-id: "anthropic/test"
            precision: HEURISTIC
            strategy: HEURISTIC
            heuristic-factor: 0.0
        default:
          tokenizer-id: "openai/cl100k_base"
          precision: HEURISTIC
          strategy: JTOKKIT
          encoding: CL100K_BASE
        """;
    ResourceLoader loader = inMemoryLoader(yaml);

    assertThatThrownBy(
            () -> new TokenizerProfileLoader(loader, new TokenizerProfileProperties(null)))
        .isInstanceOf(RuntimeException.class);
  }

  // --- Task 2.4: HF_LOCAL loading + validation ---

  @Test
  void loadsHfLocalProfile() {
    // GIVEN a YAML entry with strategy: HF_LOCAL, precision: EXACT_LOCAL, hf-model-path pointing
    // to a real classpath resource that exists.
    String yaml =
        """
        profiles:
          - provider: deepseek
            model-pattern: ".*"
            tokenizer-id: "deepseek/tokenizer"
            precision: EXACT_LOCAL
            strategy: HF_LOCAL
            hf-model-path: "deepseek/tokenizer.json"
        default:
          tokenizer-id: "openai/cl100k_base"
          precision: HEURISTIC
          strategy: JTOKKIT
          encoding: CL100K_BASE
        """;
    // Use a loader that serves YAML for the profile location and the real classpath for the vocab
    ResourceLoader loader = yamlWithRealClasspathLoader(yaml);

    TokenizerProfileLoader profileLoader =
        new TokenizerProfileLoader(loader, new TokenizerProfileProperties(null));

    ModelTokenizationProfile profile =
        profileLoader.resolve(
            dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider.DEEPSEEK, "deepseek-v3");

    assertThat(profile.strategy()).isEqualTo(TokenCounterStrategy.HF_LOCAL);
    assertThat(profile.precision()).isEqualTo(TokenizationPrecision.EXACT_LOCAL);
    assertThat(profile.hfModelPath()).isEqualTo("deepseek/tokenizer.json");
    assertThat(profile.tokenizerId()).isEqualTo("deepseek/tokenizer");
  }

  @Test
  void rejectsHfLocalMissingHfModelPath() {
    // GIVEN a YAML entry with strategy: HF_LOCAL but no hf-model-path key.
    String yaml =
        """
        profiles:
          - provider: deepseek
            model-pattern: ".*"
            tokenizer-id: "deepseek/tokenizer"
            precision: EXACT_LOCAL
            strategy: HF_LOCAL
        default:
          tokenizer-id: "openai/cl100k_base"
          precision: HEURISTIC
          strategy: JTOKKIT
          encoding: CL100K_BASE
        """;
    ResourceLoader loader = yamlWithRealClasspathLoader(yaml);

    // THEN PricingConfigurationException is thrown at startup
    assertThatThrownBy(
            () -> new TokenizerProfileLoader(loader, new TokenizerProfileProperties(null)))
        .isInstanceOf(
            dev.diegobarrioh.tokenmeter.application.pricing.PricingConfigurationException.class)
        .hasMessageContaining("hf-model-path");
  }

  @Test
  void rejectsHfLocalMissingClasspathResource() {
    // GIVEN a YAML entry with strategy: HF_LOCAL and hf-model-path pointing to an absent resource.
    String yaml =
        """
        profiles:
          - provider: deepseek
            model-pattern: ".*"
            tokenizer-id: "deepseek/tokenizer"
            precision: EXACT_LOCAL
            strategy: HF_LOCAL
            hf-model-path: "nonexistent/tokenizer.json"
        default:
          tokenizer-id: "openai/cl100k_base"
          precision: HEURISTIC
          strategy: JTOKKIT
          encoding: CL100K_BASE
        """;
    ResourceLoader loader = yamlWithRealClasspathLoader(yaml);

    // THEN PricingConfigurationException is thrown because the classpath resource does not exist
    assertThatThrownBy(
            () -> new TokenizerProfileLoader(loader, new TokenizerProfileProperties(null)))
        .isInstanceOf(
            dev.diegobarrioh.tokenmeter.application.pricing.PricingConfigurationException.class)
        .hasMessageContaining("not found");
  }

  // --- helpers ---

  /**
   * Returns a ResourceLoader that serves the given YAML content for the profile location but
   * delegates to the real classpath for all other resource lookups (e.g. vocab resources).
   */
  private static ResourceLoader yamlWithRealClasspathLoader(String yaml) {
    byte[] bytes = yaml.getBytes(StandardCharsets.UTF_8);
    Resource yamlResource =
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
    DefaultResourceLoader realClasspathLoader = new DefaultResourceLoader();
    return new ResourceLoader() {
      @Override
      public Resource getResource(String location) {
        // Serve the in-memory YAML for the profile file; delegate everything else to the real
        // classpath (so HF_LOCAL vocab existence checks resolve correctly).
        if (location != null && location.endsWith("tokenizer-profiles.yaml")) {
          return yamlResource;
        }
        return realClasspathLoader.getResource(location);
      }

      @Override
      public ClassLoader getClassLoader() {
        return TokenizerProfileLoaderTest.class.getClassLoader();
      }
    };
  }

  private static ResourceLoader inMemoryLoader(String yaml) {
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

  private static Resource missingResource() {
    return new ByteArrayResource(new byte[0]) {
      @Override
      public boolean exists() {
        return false;
      }
    };
  }

  private static ResourceLoader staticLoader(Resource resource) {
    return new ResourceLoader() {
      @Override
      public Resource getResource(String location) {
        return resource;
      }

      @Override
      public ClassLoader getClassLoader() {
        return TokenizerProfileLoaderTest.class.getClassLoader();
      }
    };
  }
}
