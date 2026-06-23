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

  // --- helpers ---

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
