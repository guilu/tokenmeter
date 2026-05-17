package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.application.cost.RepositoryCostEstimationService;
import dev.diegobarrioh.tokenmeter.application.repository.GitRepositoryCloner;
import dev.diegobarrioh.tokenmeter.application.repository.RepositoryIntakeProperties;
import dev.diegobarrioh.tokenmeter.application.repository.RepositorySizeCalculator;
import dev.diegobarrioh.tokenmeter.application.tokenizer.RepositoryTokenizationService;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.repository.GitHubRepositoryUrl;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryCloneSummary;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RepositoryAnalysisService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryAnalysisService.class);

  private final GitRepositoryCloner cloner;
  private final RepositoryIntakeProperties properties;
  private final RepositoryFileScanner fileScanner;
  private final RepositoryTokenizationService tokenizationService;
  private final AnalysisPersistenceService persistenceService;
  private final RepositoryCostEstimationService costEstimationService;
  private final RepositorySizeCalculator sizeCalculator;
  private final AnalysisConcurrencyGuard concurrencyGuard;

  @Autowired
  public RepositoryAnalysisService(
      GitRepositoryCloner cloner,
      RepositoryIntakeProperties properties,
      RepositoryFileScanner fileScanner,
      RepositoryTokenizationService tokenizationService,
      AnalysisPersistenceService persistenceService,
      RepositoryCostEstimationService costEstimationService,
      AnalysisConcurrencyGuard concurrencyGuard) {
    this(
        cloner,
        properties,
        fileScanner,
        tokenizationService,
        persistenceService,
        costEstimationService,
        new RepositorySizeCalculator(),
        concurrencyGuard);
  }

  RepositoryAnalysisService(
      GitRepositoryCloner cloner,
      RepositoryIntakeProperties properties,
      RepositoryFileScanner fileScanner,
      RepositoryTokenizationService tokenizationService,
      AnalysisPersistenceService persistenceService,
      RepositoryCostEstimationService costEstimationService,
      RepositorySizeCalculator sizeCalculator,
      AnalysisConcurrencyGuard concurrencyGuard) {
    this.cloner = cloner;
    this.properties = properties;
    this.fileScanner = fileScanner;
    this.tokenizationService = tokenizationService;
    this.persistenceService = persistenceService;
    this.costEstimationService = costEstimationService;
    this.sizeCalculator = sizeCalculator;
    this.concurrencyGuard = concurrencyGuard;
  }

  public RepositoryAnalysisResult analyze(String rawRepositoryUrl) {
    GitHubRepositoryUrl repositoryUrl = GitHubRepositoryUrl.parse(rawRepositoryUrl);
    concurrencyGuard.acquire();
    Path cloneDirectory = null;
    try {
      cloneDirectory = createCloneDirectory(repositoryUrl);
      cloner.clone(repositoryUrl, cloneDirectory, properties.cloneTimeout());
      enforceSizeLimit(sizeCalculator.summarize(cloneDirectory));
      RepositoryScanResult scan = fileScanner.scan(cloneDirectory);
      RepositoryTokenizationResult tokenization =
          tokenizationService.tokenize(cloneDirectory, scan);
      var costEstimates = costEstimationService.estimate(tokenization.totalTokens());
      return persistenceService.save(
          new RepositoryAnalysisResult(
              repositoryUrl.normalizedUrl(),
              repositoryUrl.cloneUrl(),
              repositoryUrl.owner(),
              repositoryUrl.name(),
              scan,
              tokenization,
              costEstimates));
    } finally {
      concurrencyGuard.release();
      if (cloneDirectory != null && !deleteRecursively(cloneDirectory)) {
        LOGGER.warn("Could not fully clean temporary analysis directory {}", cloneDirectory);
      }
    }
  }

  public RepositoryAnalysisResult findById(UUID id) {
    return persistenceService.findById(id).orElseThrow(() -> new AnalysisNotFoundException(id));
  }

  private Path createCloneDirectory(GitHubRepositoryUrl repositoryUrl) {
    try {
      Files.createDirectories(properties.tempDirectory());
      return Files.createTempDirectory(
          properties.tempDirectory(), repositoryUrl.owner() + "-" + repositoryUrl.name() + "-");
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not create temporary clone directory", exception);
    }
  }

  private void enforceSizeLimit(RepositoryCloneSummary summary) {
    if (summary.totalBytes() > properties.maxRepositoryBytes()) {
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.REPOSITORY_TOO_LARGE,
          "Repository size "
              + summary.totalBytes()
              + " bytes exceeds limit of "
              + properties.maxRepositoryBytes()
              + " bytes");
    }
  }

  private boolean deleteRecursively(Path path) {
    if (!Files.exists(path)) {
      return true;
    }
    try (var paths = Files.walk(path)) {
      paths.sorted(Comparator.reverseOrder()).forEach(RepositoryAnalysisService::deleteIfExists);
      return !Files.exists(path);
    } catch (IOException exception) {
      LOGGER.warn("Could not delete temporary path {}", path, exception);
      return false;
    }
  }

  private static void deleteIfExists(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not delete temporary path " + path, exception);
    }
  }
}
