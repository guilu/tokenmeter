package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RepositoryAnalysisMapper {
  public RepositoryAnalysisResponse toResponse(RepositoryAnalysisResult result) {
    var scan = result.scan();
    var tokenization = result.tokenization();
    return new RepositoryAnalysisResponse(
        result.id(),
        result.createdAt(),
        result.repositoryUrl(),
        RepositoryAnalysisStatus.SUCCESS,
        new RepositoryAnalysisMetricsResponse(
            scan.totalFiles(),
            scan.totalLines(),
            scan.totalBytes(),
            tokenization.encoding(),
            tokenization.totalTokens(),
            toLanguageMetrics(result)));
  }

  private Map<String, RepositoryAnalysisLanguageMetricsResponse> toLanguageMetrics(
      RepositoryAnalysisResult result) {
    Map<String, RepositoryAnalysisLanguageMetricsResponse> languageMetrics = new LinkedHashMap<>();
    result
        .scan()
        .languages()
        .forEach(
            (language, statistics) ->
                languageMetrics.put(language, toLanguageMetrics(result, statistics)));
    return Map.copyOf(languageMetrics);
  }

  private RepositoryAnalysisLanguageMetricsResponse toLanguageMetrics(
      RepositoryAnalysisResult result, LanguageStatistics statistics) {
    LanguageTokenMetrics tokenMetrics =
        result.tokenization().languages().get(statistics.language());
    long tokens = tokenMetrics == null ? 0 : tokenMetrics.tokens();
    return new RepositoryAnalysisLanguageMetricsResponse(
        statistics.language(), statistics.files(), statistics.lines(), statistics.bytes(), tokens);
  }
}
