package dev.diegobarrioh.tokenmeter.infrastructure.tokenizer;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import dev.diegobarrioh.tokenmeter.application.tokenizer.TokenCounter;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenCounterStrategy;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * {@link TokenCounter} implementation for {@link TokenCounterStrategy#HF_LOCAL} profiles. Loads
 * vendored {@code tokenizer.json} files from the classpath via the DJL HuggingFace Tokenizers
 * library and returns exact token counts.
 *
 * <p>Design decisions:
 *
 * <ul>
 *   <li>Tokenizer instances are loaded lazily on first {@link #count} call and cached per {@code
 *       hfModelPath} in a {@link ConcurrentHashMap}. Identical paths share one native instance
 *       (deduplication).
 *   <li>The tokenizer is loaded via {@link HuggingFaceTokenizer#newInstance(InputStream, Map)} —
 *       the InputStream factory — which works correctly inside a fat jar. The Path-based factory
 *       fails for classpath resources packed in a JAR.
 *   <li>On any load or native failure, {@link #count} returns {@code 0L} and logs a WARN. The
 *       analysis pipeline MUST continue; zero counts are visible in results and in logs. A failed
 *       load returns {@code null}, which {@link ConcurrentHashMap#computeIfAbsent} does NOT store —
 *       so a missing/corrupt resource is re-attempted on the next call. This is acceptable because
 *       configured HF_LOCAL profiles are guarded by the loader's startup fail-fast; the retry only
 *       affects a misconfigured/runtime-bypassed path and is perf-only (an extra WARN per call).
 *   <li>Thread safety: {@code computeIfAbsent} guarantees at most one native load per key; {@code
 *       HuggingFaceTokenizer.encode()} relies on the Rust core being {@code Send + Sync}
 *       (documented in DJL; concurrent calls are safe once the instance is constructed).
 *   <li>At Spring context shutdown ({@link PreDestroy}) all cached instances are closed to release
 *       JNI / native memory.
 * </ul>
 */
@Component
public class HuggingFaceTokenCounter implements TokenCounter {

  private static final Logger LOG = LoggerFactory.getLogger(HuggingFaceTokenCounter.class);

  private final ResourceLoader resourceLoader;

  /**
   * Cache keyed by {@code hfModelPath} (classpath-relative path, e.g. {@code
   * "deepseek/tokenizer.json"}). Only successful loads are stored; a failed load returns {@code
   * null}, which {@code computeIfAbsent} does not cache, so it is re-attempted next call.
   */
  private final ConcurrentHashMap<String, HuggingFaceTokenizer> cache = new ConcurrentHashMap<>();

  /** Constructor injection; Spring selects this automatically (single public constructor). */
  public HuggingFaceTokenCounter(ResourceLoader resourceLoader) {
    this.resourceLoader = resourceLoader;
  }

  @Override
  public boolean supports(ModelTokenizationProfile profile) {
    return profile.strategy() == TokenCounterStrategy.HF_LOCAL;
  }

  @Override
  public long count(String text, ModelTokenizationProfile profile) {
    if (text == null || text.isEmpty()) {
      return 0L;
    }
    HuggingFaceTokenizer tokenizer = resolveTokenizer(profile.hfModelPath());
    if (tokenizer == null) {
      return 0L;
    }
    try {
      return tokenizer.encode(text).getIds().length;
    } catch (RuntimeException | LinkageError e) {
      LOG.warn("HF encode failed for tokenizer '{}': {}", profile.tokenizerId(), e.getMessage());
      return 0L;
    }
  }

  /**
   * Closes all cached {@link HuggingFaceTokenizer} instances to release native JNI memory. Called
   * automatically by Spring on application context shutdown.
   */
  @PreDestroy
  public void close() {
    cache
        .values()
        .forEach(
            tokenizer -> {
              if (tokenizer != null) {
                try {
                  tokenizer.close();
                } catch (RuntimeException ignored) {
                  // Best-effort close; suppress to avoid masking shutdown errors.
                }
              }
            });
  }

  /**
   * Loads the tokenizer for the given classpath-relative path, caching successful loads. Returns
   * {@code null} if the resource is missing or the tokenizer fails to initialise; a null is not
   * cached by {@code computeIfAbsent}, so the load is re-attempted on the next call.
   */
  private HuggingFaceTokenizer resolveTokenizer(String classpathRelPath) {
    // computeIfAbsent guarantees at most one successful load per key even under concurrent access.
    // A null return (failed load) is not stored, so a later call retries.
    return cache.computeIfAbsent(
        classpathRelPath,
        key -> {
          try {
            Resource resource = resourceLoader.getResource("classpath:tokenizers/" + key);
            try (InputStream inputStream = resource.getInputStream()) {
              return HuggingFaceTokenizer.newInstance(inputStream, Map.of());
            }
          } catch (IOException | RuntimeException | LinkageError e) {
            // LinkageError (e.g. UnsatisfiedLinkError) is thrown when the DJL native lib cannot be
            // loaded — e.g. /tmp mounted noexec. It is an Error, not an Exception, so it MUST be
            // caught here or it would propagate and break the whole analysis job.
            LOG.warn(
                "HF tokenizer load failed for classpath:tokenizers/{}: {}", key, e.getMessage());
            return null;
          }
        });
  }
}
