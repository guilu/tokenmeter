package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(JpaAnalysisPersistenceService.class)
class JpaAnalysisPersistenceServiceTest {
  @Autowired private JpaAnalysisPersistenceService persistenceService;
  @Autowired private AnalysisJpaRepository repository;

  @Test
  void persistsCompletedAnalysisAndLanguageStatistics() {
    RepositoryAnalysisResult saved = persistenceService.save(sampleResult());

    assertThat(saved.id()).isNotNull();
    assertThat(saved.createdAt()).isNotNull();
    assertThat(repository.findById(saved.id())).isPresent();
    assertThat(saved.scan().languages().get("Java").files()).isEqualTo(2);
    assertThat(saved.tokenization().languages().get("Java").tokens()).isEqualTo(25);
  }

  @Test
  void retrievesAnalysisById() {
    RepositoryAnalysisResult saved = persistenceService.save(sampleResult());

    assertThat(persistenceService.findById(saved.id()))
        .isPresent()
        .get()
        .satisfies(
            result -> {
              assertThat(result.repositoryUrl()).isEqualTo("https://github.com/guilu/tokenmeter");
              assertThat(result.scan().totalFiles()).isEqualTo(3);
              assertThat(result.tokenization().totalTokens()).isEqualTo(40);
              assertThat(result.scan().languages()).containsKeys("Java", "TypeScript");
            });
  }

  @Test
  void returnsEmptyForNonexistentAnalysis() {
    assertThat(persistenceService.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void deletesLanguageStatsThroughForeignKeyRelationship() {
    RepositoryAnalysisResult saved = persistenceService.save(sampleResult());

    repository.deleteById(saved.id());
    repository.flush();

    assertThat(repository.findById(saved.id())).isEmpty();
  }

  private static RepositoryAnalysisResult sampleResult() {
    return new RepositoryAnalysisResult(
        "https://github.com/guilu/tokenmeter",
        "https://github.com/guilu/tokenmeter.git",
        "guilu",
        "tokenmeter",
        new RepositoryScanResult(
            3,
            30,
            300,
            List.of(),
            Map.of(
                "Java", new LanguageStatistics("Java", 2, 20, 200),
                "TypeScript", new LanguageStatistics("TypeScript", 1, 10, 100))),
        new RepositoryTokenizationResult(
            "o200k_base",
            3,
            40,
            List.of(),
            Map.of(
                "Java", new LanguageTokenMetrics("Java", 2, 25),
                "TypeScript", new LanguageTokenMetrics("TypeScript", 1, 15))));
  }
}
