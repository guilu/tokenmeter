package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.application.cost.RepositoryCostEstimationService;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.application.repository.RepositoryIntakeProperties;
import dev.diegobarrioh.tokenmeter.application.tokenizer.OpenAiTokenCounter;
import dev.diegobarrioh.tokenmeter.application.tokenizer.RepositoryTokenizationService;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryAnalysisServiceTest {
  @TempDir Path tempDir;

  @Test
  void clonesScansAndCleansRepository() {
    RepositoryAnalysisService service =
        new RepositoryAnalysisService(
            (repositoryUrl, targetDirectory) -> {
              writeFile(targetDirectory, "src/App.java", "class App {}\n");
              writeFile(targetDirectory, "node_modules/pkg/index.js", "ignored\n");
            },
            properties(1024, Duration.ofSeconds(2)),
            scanner(),
            tokenizationService(),
            persistenceService(),
            costEstimationService(),
            unlimitedGuard());

    RepositoryAnalysisResult result = service.analyze("https://github.com/guilu/tokenmeter");

    assertThat(result.id()).isNotNull();
    assertThat(result.createdAt()).isNotNull();
    assertThat(result.repositoryUrl()).isEqualTo("https://github.com/guilu/tokenmeter");
    assertThat(result.cloneUrl()).isEqualTo("https://github.com/guilu/tokenmeter.git");
    assertThat(result.owner()).isEqualTo("guilu");
    assertThat(result.name()).isEqualTo("tokenmeter");
    assertThat(result.scan().totalFiles()).isEqualTo(1);
    assertThat(result.scan().totalLines()).isEqualTo(1);
    assertThat(result.scan().languages().get("Java").files()).isEqualTo(1);
    assertThat(result.tokenization().encoding()).isEqualTo("o200k_base");
    assertThat(result.tokenization().totalFiles()).isEqualTo(1);
    assertThat(result.tokenization().totalTokens()).isPositive();
    assertThat(result.tokenization().languages().get("Java").tokens()).isPositive();
    assertThat(result.costEstimates()).hasSize(3);
    assertThat(result.costEstimates())
        .allSatisfy(
            estimate ->
                assertThat(estimate.baseTokens()).isEqualTo(result.tokenization().totalTokens()));
    assertThat(tempDir).isEmptyDirectory();
  }

  @Test
  void mapsCloneTimeoutAndCleansDirectory() {
    RepositoryAnalysisService service =
        new RepositoryAnalysisService(
            (repositoryUrl, targetDirectory) -> sleep(Duration.ofMillis(500)),
            properties(1024, Duration.ofMillis(50)),
            scanner(),
            tokenizationService(),
            persistenceService(),
            costEstimationService(),
            unlimitedGuard());

    assertThatThrownBy(() -> service.analyze("https://github.com/guilu/slow"))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.CLONE_TIMEOUT);
    assertThat(tempDir).isEmptyDirectory();
  }

  @Test
  void enforcesRepositorySizeLimitBeforeReturningAnalysis() {
    RepositoryAnalysisService service =
        new RepositoryAnalysisService(
            (repositoryUrl, targetDirectory) ->
                writeFile(targetDirectory, "large.txt", "too large"),
            properties(3, Duration.ofSeconds(2)),
            scanner(),
            tokenizationService(),
            persistenceService(),
            costEstimationService(),
            unlimitedGuard());

    assertThatThrownBy(() -> service.analyze("https://github.com/guilu/huge"))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.REPOSITORY_TOO_LARGE);
    assertThat(tempDir).isEmptyDirectory();
  }

  private RepositoryIntakeProperties properties(long maxBytes, Duration timeout) {
    return new RepositoryIntakeProperties(tempDir, maxBytes, timeout);
  }

  private static AnalysisConcurrencyGuard unlimitedGuard() {
    return new AnalysisConcurrencyGuard(new AnalyzeThrottleProperties(Integer.MAX_VALUE, 0, null));
  }

  private static RepositoryFileScanner scanner() {
    return new RepositoryFileScanner(new FileLanguageDetector(), new BinaryFileDetector());
  }

  private static RepositoryTokenizationService tokenizationService() {
    return new RepositoryTokenizationService(new OpenAiTokenCounter());
  }

  private static AnalysisPersistenceService persistenceService() {
    return new AnalysisPersistenceService() {
      @Override
      public RepositoryAnalysisResult save(RepositoryAnalysisResult result) {
        return new RepositoryAnalysisResult(
            UUID.randomUUID(),
            Instant.now(),
            result.repositoryUrl(),
            result.cloneUrl(),
            result.owner(),
            result.name(),
            result.scan(),
            result.tokenization(),
            result.costEstimates());
      }

      @Override
      public Optional<RepositoryAnalysisResult> findById(UUID id) {
        return Optional.empty();
      }
    };
  }

  private static RepositoryCostEstimationService costEstimationService() {
    return new RepositoryCostEstimationService(
        new PricingProvider() {
          @Override
          public List<ModelPricing> all() {
            return List.of(
                new ModelPricing(
                    AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")));
          }

          @Override
          public Optional<ModelPricing> find(AiProvider provider, String model) {
            return Optional.empty();
          }
        });
  }

  private static void writeFile(Path directory, String relativePath, String content) {
    try {
      Path file = directory.resolve(relativePath);
      Files.createDirectories(file.getParent());
      Files.writeString(file, content);
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
