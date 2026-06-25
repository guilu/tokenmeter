package dev.diegobarrioh.tokenmeter.infrastructure.tokenizer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenCounterStrategy;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenizationPrecision;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Unit tests for {@link HuggingFaceTokenCounter}. No Spring context — loads vocab from the
 * classpath directly via {@link DefaultResourceLoader} (same mechanism as production).
 *
 * <p>Golden counts were captured from the vendored {@code tokenizer.json} files:
 *
 * <ul>
 *   <li>DeepSeek ({@code tokenizers/deepseek/tokenizer.json}) — vocab size 128,000
 *   <li>Qwen ({@code tokenizers/qwen/tokenizer.json}) — vocab size 151,643
 * </ul>
 *
 * SHA-256 of vendored files (confirmed distinct):
 *
 * <ul>
 *   <li>deepseek: {@code 621ac2e32d0dba658404412318818aaa8ce8cda492e59830109d8da6b517fb41}
 *   <li>qwen: {@code c0382117ea329cdf097041132f6d735924b697924d6f6fc3945713e96ce87539}
 * </ul>
 */
class HuggingFaceTokenCounterTest {

  // Golden token counts for "Hello, world!" — captured from the real vendored tokenizer.json files.
  // Task 1.2: these constants are pinned from the actual DJL tokenizer output.
  // Interestingly, both vocabularies produce the same count for this specific string,
  // even though they have different vocab sizes (DeepSeek: 128,000; Qwen: 151,643).
  private static final long DEEPSEEK_HELLO_WORLD_TOKENS =
      4L; // verified: ["Hello", ",", " world", "!"]
  private static final long QWEN_HELLO_WORLD_TOKENS = 4L; // verified: same segmentation

  private HuggingFaceTokenCounter counter =
      new HuggingFaceTokenCounter(new DefaultResourceLoader());

  @AfterEach
  void closeCounter() {
    counter.close();
  }

  // --- Task 2.1: supports() ---

  @Test
  void supportsHfLocalProfile() {
    ModelTokenizationProfile hfProfile = deepseekProfile();
    assertThat(counter.supports(hfProfile)).isTrue();
  }

  @Test
  void doesNotSupportJtokkitProfile() {
    ModelTokenizationProfile jtokkit =
        new ModelTokenizationProfile(
            "openai/o200k_base",
            TokenizationPrecision.EXACT_LOCAL,
            TokenCounterStrategy.JTOKKIT,
            "O200K_BASE",
            null,
            null);
    assertThat(counter.supports(jtokkit)).isFalse();
  }

  @Test
  void doesNotSupportHeuristicProfile() {
    ModelTokenizationProfile heuristic =
        new ModelTokenizationProfile(
            "anthropic/cl100k_heuristic",
            TokenizationPrecision.HEURISTIC,
            TokenCounterStrategy.HEURISTIC,
            null,
            new java.math.BigDecimal("0.95"),
            null);
    assertThat(counter.supports(heuristic)).isFalse();
  }

  // --- Task 2.2: count / cache / fallback ---

  @Test
  void countsKnownStringDeterministically_deepseek() {
    long count = counter.count("Hello, world!", deepseekProfile());
    assertThat(count).isEqualTo(DEEPSEEK_HELLO_WORLD_TOKENS);
  }

  @Test
  void countsKnownStringDeterministically_qwen() {
    long count = counter.count("Hello, world!", qwenProfile());
    assertThat(count).isEqualTo(QWEN_HELLO_WORLD_TOKENS);
  }

  @Test
  void deepseekAndQwenAreDistinctTokenizers() {
    // "Hello, world!" happens to segment to 4 in both vocabularies. A multilingual/code string
    // exercises the vocabulary differences (DeepSeek 128k vs Qwen 151,643), proving the counter
    // loads and uses two DIFFERENT native tokenizers rather than one shared instance.
    String text = "函数 def 计算(x): return x * 2  # 注释 αβγ ☕";
    long deepseek = counter.count(text, deepseekProfile());
    long qwen = counter.count(text, qwenProfile());
    assertThat(deepseek).isPositive();
    assertThat(qwen).isPositive();
    assertThat(deepseek)
        .as("DeepSeek and Qwen use different vocabularies and must segment this string differently")
        .isNotEqualTo(qwen);
  }

  @Test
  void emptyStringReturnsZero() {
    assertThat(counter.count("", deepseekProfile())).isZero();
    assertThat(counter.count("", qwenProfile())).isZero();
  }

  @Test
  void nonEmptyStringReturnsPositiveCount() {
    assertThat(counter.count("Hello, world!", deepseekProfile())).isPositive();
    assertThat(counter.count("Hello, world!", qwenProfile())).isPositive();
  }

  @Test
  void cachesOneInstancePerTokenizerId() {
    // Two calls to the same tokenizerId — counter should reuse the same instance.
    // We verify this indirectly: both calls return the same count (deterministic), and the counter
    // object reference itself is unchanged (cache hit path doesn't rebuild).
    // A CountingResourceLoader subclass is used to spy on load calls.
    int[] loadCount = {0};
    HuggingFaceTokenCounter spyCounter =
        new HuggingFaceTokenCounter(
            new DefaultResourceLoader() {
              @Override
              public org.springframework.core.io.Resource getResource(String location) {
                if (location.contains("tokenizer.json")) {
                  loadCount[0]++;
                }
                return super.getResource(location);
              }
            });

    try {
      spyCounter.count("Hello", deepseekProfile());
      spyCounter.count("World", deepseekProfile());
      // Two count calls → only ONE getResource call (loaded once, cached thereafter)
      assertThat(loadCount[0]).isEqualTo(1);
    } finally {
      spyCounter.close();
    }
  }

  @Test
  void missingResourceFallsBackToZeroWithoutThrowing() {
    ModelTokenizationProfile missingProfile =
        new ModelTokenizationProfile(
            "test/missing",
            TokenizationPrecision.EXACT_LOCAL,
            TokenCounterStrategy.HF_LOCAL,
            null,
            null,
            "nonexistent/tokenizer.json");

    long result = counter.count("Hello, world!", missingProfile);
    assertThat(result).isZero();
  }

  @Test
  void corruptResourceFallsBackToZeroWithoutThrowing() {
    // The corrupt resource is provided via a separate counter with an in-memory corrupt resource.
    HuggingFaceTokenCounter corruptCounter =
        new HuggingFaceTokenCounter(
            new DefaultResourceLoader() {
              @Override
              public org.springframework.core.io.Resource getResource(String location) {
                if (location.contains("corrupt")) {
                  return new org.springframework.core.io.ByteArrayResource(
                      "not valid json at all {{{".getBytes()) {
                    @Override
                    public boolean exists() {
                      return true;
                    }
                  };
                }
                return super.getResource(location);
              }
            });
    try {
      ModelTokenizationProfile corruptProfile =
          new ModelTokenizationProfile(
              "test/corrupt",
              TokenizationPrecision.EXACT_LOCAL,
              TokenCounterStrategy.HF_LOCAL,
              null,
              null,
              "corrupt/tokenizer.json");

      long result = corruptCounter.count("Hello, world!", corruptProfile);
      assertThat(result).isZero();
    } finally {
      corruptCounter.close();
    }
  }

  @Test
  void nativeLinkageErrorFallsBackToZeroWithoutThrowing() {
    // The DJL native lib can fail to load with UnsatisfiedLinkError (a LinkageError / Error, NOT an
    // Exception) — e.g. /tmp mounted noexec in production. count() MUST degrade to 0L, never let
    // the Error propagate and break the analysis job.
    HuggingFaceTokenCounter nativeFailingCounter =
        new HuggingFaceTokenCounter(
            new DefaultResourceLoader() {
              @Override
              public org.springframework.core.io.Resource getResource(String location) {
                if (location.contains("native-fail")) {
                  return new org.springframework.core.io.ByteArrayResource(new byte[0]) {
                    @Override
                    public boolean exists() {
                      return true;
                    }

                    @Override
                    public java.io.InputStream getInputStream() {
                      throw new UnsatisfiedLinkError("simulated native lib failure");
                    }
                  };
                }
                return super.getResource(location);
              }
            });
    try {
      ModelTokenizationProfile profile =
          new ModelTokenizationProfile(
              "test/native-fail",
              TokenizationPrecision.EXACT_LOCAL,
              TokenCounterStrategy.HF_LOCAL,
              null,
              null,
              "native-fail/tokenizer.json");

      long result = nativeFailingCounter.count("Hello, world!", profile);
      assertThat(result).isZero();
    } finally {
      nativeFailingCounter.close();
    }
  }

  @Test
  void preDestroyClosesAllCachedTokenizers() {
    // Load both tokenizers into the cache
    counter.count("Hello", deepseekProfile());
    counter.count("Hello", qwenProfile());
    // close() must not throw even after both are loaded
    counter.close();
    // Re-initialize so @AfterEach doesn't fail
    counter = new HuggingFaceTokenCounter(new DefaultResourceLoader());
  }

  // --- helpers ---

  private static ModelTokenizationProfile deepseekProfile() {
    return new ModelTokenizationProfile(
        "deepseek/tokenizer",
        TokenizationPrecision.EXACT_LOCAL,
        TokenCounterStrategy.HF_LOCAL,
        null,
        null,
        "deepseek/tokenizer.json");
  }

  private static ModelTokenizationProfile qwenProfile() {
    return new ModelTokenizationProfile(
        "qwen/tokenizer",
        TokenizationPrecision.EXACT_LOCAL,
        TokenCounterStrategy.HF_LOCAL,
        null,
        null,
        "qwen/tokenizer.json");
  }
}
