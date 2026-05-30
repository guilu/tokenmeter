package dev.diegobarrioh.tokenmeter.application.tokenizer;

import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryFileMetric;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.FileTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RepositoryTokenizationService {
  private static final int CHUNK_SIZE = 8192;

  private final OpenAiTokenCounter tokenCounter;

  public RepositoryTokenizationService(OpenAiTokenCounter tokenCounter) {
    this.tokenCounter = tokenCounter;
  }

  public RepositoryTokenizationResult tokenize(
      Path repositoryRoot, RepositoryScanResult scan, TokenizationProgressListener listener) {
    List<FileTokenMetrics> files = new ArrayList<>(scan.files().size());
    long total = scan.files().size();
    long tokensSoFar = 0L;
    long processed = 0L;
    for (var file : scan.files()) {
      FileTokenMetrics metric = tokenizeFile(repositoryRoot, file);
      files.add(metric);
      processed++;
      tokensSoFar += metric.tokens();
      listener.onProgress(processed, total, tokensSoFar);
    }
    return summarize(files);
  }

  private FileTokenMetrics tokenizeFile(Path repositoryRoot, RepositoryFileMetric file) {
    Path absolutePath = repositoryRoot.resolve(file.relativePath()).normalize();
    return new FileTokenMetrics(
        file.relativePath(), file.language(), countFileTokens(absolutePath));
  }

  private long countFileTokens(Path file) {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      char[] buffer = new char[CHUNK_SIZE];
      long tokens = 0;
      int charactersRead;
      while ((charactersRead = reader.read(buffer)) != -1) {
        tokens += tokenCounter.count(new String(buffer, 0, charactersRead));
      }
      return tokens;
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not tokenize file " + file, exception);
    }
  }

  private RepositoryTokenizationResult summarize(List<FileTokenMetrics> files) {
    long totalTokens = files.stream().mapToLong(FileTokenMetrics::tokens).sum();
    Map<String, MutableLanguageTokenMetrics> mutableLanguages = new LinkedHashMap<>();

    files.forEach(
        file ->
            mutableLanguages
                .computeIfAbsent(file.language(), MutableLanguageTokenMetrics::new)
                .add(file.tokens()));

    Map<String, LanguageTokenMetrics> languages = new LinkedHashMap<>();
    mutableLanguages.forEach((language, metrics) -> languages.put(language, metrics.toRecord()));

    return new RepositoryTokenizationResult(
        tokenCounter.encodingName(),
        files.size(),
        totalTokens,
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
