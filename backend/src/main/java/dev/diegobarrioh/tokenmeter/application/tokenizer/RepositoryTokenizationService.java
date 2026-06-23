package dev.diegobarrioh.tokenmeter.application.tokenizer;

import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryFileMetric;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.FileTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenCounterStrategy;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Tokenizes all files in a repository in a single I/O pass per file, applying each required
 * tokenizer in turn so the file is only read from disk once regardless of how many tokenizers are
 * active.
 *
 * <p>The service is pricing-free: it receives the distinct {@code tokenizerId → profile} map
 * pre-resolved by {@code ModelTokenizationProfileResolver} in the executor, keeping the
 * tokenization concern decoupled from pricing.
 *
 * <p>Optimisation: the o200k reference count is computed once per chunk; HEURISTIC counters scale
 * it rather than re-encoding. This keeps the worst case to (distinct JTOKKIT encodings) jtokkit
 * passes per chunk, not (number of models).
 */
@Service
public class RepositoryTokenizationService {
  private static final int CHUNK_SIZE = 8192;

  /** Kept for legacy Spring wiring and as the o200k reference counter for heuristics. */
  private final OpenAiTokenCounter openAiTokenCounter;

  private final TokenCounterRegistry registry;

  public RepositoryTokenizationService(
      OpenAiTokenCounter openAiTokenCounter, TokenCounterRegistry registry) {
    this.openAiTokenCounter = openAiTokenCounter;
    this.registry = registry;
  }

  /**
   * Tokenizes the repository using only the distinct tokenizers in {@code requiredTokenizers}.
   *
   * <p>The returned {@link RepositoryTokenizationResult#totalTokens()} is always the o200k_base
   * total so existing callers (leaderboard, badge, markdown export, language stats) are unaffected.
   *
   * @param repositoryRoot absolute path to the checked-out repository
   * @param scan filtered file list from the scanner
   * @param requiredTokenizers map from tokenizerId to profile; determines which tokenizers run
   * @param listener progress callback; invoked once per file
   * @return aggregated tokenization result
   */
  public RepositoryTokenizationResult tokenize(
      Path repositoryRoot,
      RepositoryScanResult scan,
      Map<String, ModelTokenizationProfile> requiredTokenizers,
      TokenizationProgressListener listener) {

    // Initialise per-tokenizer running totals to 0 even for empty repos
    Map<String, Long> globalTotals = new LinkedHashMap<>();
    requiredTokenizers.keySet().forEach(id -> globalTotals.put(id, 0L));

    List<FileTokenMetrics> files = new ArrayList<>(scan.files().size());
    long total = scan.files().size();
    long totalPrimaryTokens = 0L;
    long processed = 0L;

    for (RepositoryFileMetric file : scan.files()) {
      FileTokenMetrics metric = tokenizeFile(repositoryRoot, file, requiredTokenizers);
      files.add(metric);
      processed++;

      // Accumulate per-tokenizer totals
      metric.tokensByTokenizerId().forEach((id, count) -> globalTotals.merge(id, count, Long::sum));

      totalPrimaryTokens = globalTotals.getOrDefault(FileTokenMetrics.PRIMARY_TOKENIZER_ID, 0L);
      listener.onProgress(processed, total, totalPrimaryTokens);
    }

    return summarize(files, globalTotals, totalPrimaryTokens);
  }

  private FileTokenMetrics tokenizeFile(
      Path repositoryRoot,
      RepositoryFileMetric file,
      Map<String, ModelTokenizationProfile> requiredTokenizers) {
    Path absolutePath = repositoryRoot.resolve(file.relativePath()).normalize();
    Map<String, Long> counts = countTokensPerProfile(absolutePath, requiredTokenizers);
    return new FileTokenMetrics(file.relativePath(), file.language(), Map.copyOf(counts));
  }

  /**
   * Single I/O pass: reads the file in 8192-char chunks; per chunk, accumulates counts for each
   * required tokenizer. The o200k reference count is computed once per chunk and reused by
   * HEURISTIC counters to avoid redundant encoding passes.
   */
  private Map<String, Long> countTokensPerProfile(
      Path file, Map<String, ModelTokenizationProfile> requiredTokenizers) {
    Map<String, Long> totals = new HashMap<>();
    requiredTokenizers.keySet().forEach(id -> totals.put(id, 0L));

    if (requiredTokenizers.isEmpty()) {
      return totals;
    }

    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      char[] buffer = new char[CHUNK_SIZE];
      int charactersRead;
      while ((charactersRead = reader.read(buffer)) != -1) {
        String chunk = new String(buffer, 0, charactersRead);
        if (chunk.isEmpty()) {
          continue;
        }
        // Compute the o200k reference count once per chunk (HEURISTIC counters scale this)
        long referenceCount = openAiTokenCounter.count(chunk);

        for (Map.Entry<String, ModelTokenizationProfile> entry : requiredTokenizers.entrySet()) {
          String id = entry.getKey();
          ModelTokenizationProfile profile = entry.getValue();
          long chunkCount = countChunk(chunk, profile, referenceCount);
          totals.merge(id, chunkCount, Long::sum);
        }
      }
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not tokenize file " + file, exception);
    }
    return totals;
  }

  /**
   * Counts tokens for a single chunk using the profile's strategy.
   *
   * <p>For {@code JTOKKIT} profiles whose encoding is {@code O200K_BASE}, the pre-computed
   * reference count is reused directly to avoid a second jtokkit pass. For other JTOKKIT encodings
   * the registry counter is called normally. HEURISTIC counters receive the chunk text and let
   * {@link HeuristicTokenCounter} scale the reference internally.
   */
  private long countChunk(String chunk, ModelTokenizationProfile profile, long referenceCount) {
    // Optimisation: o200k JTOKKIT profile reuses the already-computed reference count
    if (profile.strategy() == TokenCounterStrategy.JTOKKIT
        && "O200K_BASE".equalsIgnoreCase(profile.encoding())) {
      return referenceCount;
    }
    TokenCounter counter = registry.resolve(profile);
    return counter.count(chunk, profile);
  }

  private RepositoryTokenizationResult summarize(
      List<FileTokenMetrics> files,
      Map<String, Long> tokensByTokenizerId,
      long totalPrimaryTokens) {

    Map<String, MutableLanguageTokenMetrics> mutableLanguages = new LinkedHashMap<>();
    files.forEach(
        file ->
            mutableLanguages
                .computeIfAbsent(file.language(), MutableLanguageTokenMetrics::new)
                .add(file.tokens()));

    Map<String, LanguageTokenMetrics> languages = new LinkedHashMap<>();
    mutableLanguages.forEach((language, metrics) -> languages.put(language, metrics.toRecord()));

    return new RepositoryTokenizationResult(
        openAiTokenCounter.encodingName(),
        files.size(),
        totalPrimaryTokens,
        Map.copyOf(tokensByTokenizerId),
        List.copyOf(files),
        Map.copyOf(languages));
  }

  private static final class MutableLanguageTokenMetrics {
    private final String language;
    private long files;
    private long tokens;

    private MutableLanguageTokenMetrics(String language) {
      this.language = language;
    }

    private void add(long fileTokens) {
      files++;
      tokens += fileTokens;
    }

    private LanguageTokenMetrics toRecord() {
      return new LanguageTokenMetrics(language, files, tokens);
    }
  }
}
