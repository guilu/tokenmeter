package dev.diegobarrioh.tokenmeter.application.repository;

import dev.diegobarrioh.tokenmeter.domain.repository.GitHubRepositoryUrl;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryCloneSummary;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RepositoryIntakeService {
  private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryIntakeService.class);

  private final GitRepositoryCloner cloner;
  private final RepositoryIntakeProperties properties;
  private final RepositorySizeCalculator sizeCalculator;

  @Autowired
  public RepositoryIntakeService(
      GitRepositoryCloner cloner, RepositoryIntakeProperties properties) {
    this(cloner, properties, new RepositorySizeCalculator());
  }

  RepositoryIntakeService(
      GitRepositoryCloner cloner,
      RepositoryIntakeProperties properties,
      RepositorySizeCalculator sizeCalculator) {
    this.cloner = cloner;
    this.properties = properties;
    this.sizeCalculator = sizeCalculator;
  }

  public RepositoryIntakeResult intake(String rawRepositoryUrl) {
    GitHubRepositoryUrl repositoryUrl = GitHubRepositoryUrl.parse(rawRepositoryUrl);
    Path cloneDirectory = createCloneDirectory(repositoryUrl);
    boolean cleanedUp = false;

    try {
      cloner.clone(repositoryUrl, cloneDirectory, properties.cloneTimeout());
      RepositoryCloneSummary summary = sizeCalculator.summarize(cloneDirectory);
      enforceSizeLimit(summary);
      return new RepositoryIntakeResult(
          repositoryUrl.normalizedUrl(),
          repositoryUrl.cloneUrl(),
          repositoryUrl.owner(),
          repositoryUrl.name(),
          summary.totalBytes(),
          summary.fileCount(),
          true);
    } finally {
      cleanedUp = deleteRecursively(cloneDirectory);
      if (!cleanedUp) {
        LOGGER.warn("Could not fully clean temporary clone directory {}", cloneDirectory);
      }
    }
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
      paths.sorted(Comparator.reverseOrder()).forEach(RepositoryIntakeService::deleteIfExists);
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
