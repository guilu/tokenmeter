package dev.diegobarrioh.tokenmeter.application.analyzer;

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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
  private final RepositorySizeCalculator sizeCalculator;

  @Autowired
  public RepositoryAnalysisService(
      GitRepositoryCloner cloner,
      RepositoryIntakeProperties properties,
      RepositoryFileScanner fileScanner,
      RepositoryTokenizationService tokenizationService,
      AnalysisPersistenceService persistenceService) {
    this(
        cloner,
        properties,
        fileScanner,
        tokenizationService,
        persistenceService,
        new RepositorySizeCalculator());
  }

  RepositoryAnalysisService(
      GitRepositoryCloner cloner,
      RepositoryIntakeProperties properties,
      RepositoryFileScanner fileScanner,
      RepositoryTokenizationService tokenizationService,
      AnalysisPersistenceService persistenceService,
      RepositorySizeCalculator sizeCalculator) {
    this.cloner = cloner;
    this.properties = properties;
    this.fileScanner = fileScanner;
    this.tokenizationService = tokenizationService;
    this.persistenceService = persistenceService;
    this.sizeCalculator = sizeCalculator;
  }

  public RepositoryAnalysisResult analyze(String rawRepositoryUrl) {
    GitHubRepositoryUrl repositoryUrl = GitHubRepositoryUrl.parse(rawRepositoryUrl);
    Path cloneDirectory = createCloneDirectory(repositoryUrl);

    try {
      cloneWithTimeout(repositoryUrl, cloneDirectory);
      enforceSizeLimit(sizeCalculator.summarize(cloneDirectory));
      RepositoryScanResult scan = fileScanner.scan(cloneDirectory);
      RepositoryTokenizationResult tokenization =
          tokenizationService.tokenize(cloneDirectory, scan);
      return persistenceService.save(
          new RepositoryAnalysisResult(
              repositoryUrl.normalizedUrl(),
              repositoryUrl.cloneUrl(),
              repositoryUrl.owner(),
              repositoryUrl.name(),
              scan,
              tokenization));
    } finally {
      if (!deleteRecursively(cloneDirectory)) {
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

  private void cloneWithTimeout(GitHubRepositoryUrl repositoryUrl, Path cloneDirectory) {
    var executor = Executors.newSingleThreadExecutor();
    try {
      var future = executor.submit(cloneTask(repositoryUrl, cloneDirectory));
      future.get(properties.cloneTimeout().toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.CLONE_TIMEOUT,
          "Repository clone exceeded timeout of "
              + properties.cloneTimeout().toSeconds()
              + " seconds",
          exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.CLONE_TIMEOUT, "Repository clone was interrupted", exception);
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause();
      if (cause instanceof RepositoryIntakeException intakeException) {
        throw intakeException;
      }
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.CLONE_FAILED, "Repository clone failed", cause);
    } finally {
      executor.shutdownNow();
    }
  }

  private Callable<Void> cloneTask(GitHubRepositoryUrl repositoryUrl, Path cloneDirectory) {
    return () -> {
      cloner.clone(repositoryUrl, cloneDirectory);
      return null;
    };
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
