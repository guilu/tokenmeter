package dev.diegobarrioh.tokenmeter.application.repository;

import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryCloneSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RepositorySizeCalculator {
  public RepositoryCloneSummary summarize(Path repositoryDirectory) {
    try (var paths = Files.walk(repositoryDirectory)) {
      var files =
          paths
              .filter(path -> isRepositoryFile(repositoryDirectory, path))
              .map(RepositorySizeCalculator::fileSize)
              .toList();
      long totalBytes = files.stream().mapToLong(Long::longValue).sum();
      return new RepositoryCloneSummary(totalBytes, files.size());
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not inspect cloned repository", exception);
    }
  }

  private static boolean isRepositoryFile(Path repositoryDirectory, Path path) {
    return Files.isRegularFile(path) && !isGitMetadata(repositoryDirectory, path);
  }

  private static boolean isGitMetadata(Path repositoryDirectory, Path path) {
    Path relativePath = repositoryDirectory.relativize(path);
    return relativePath.getNameCount() > 0 && relativePath.getName(0).toString().equals(".git");
  }

  private static long fileSize(Path path) {
    try {
      return Files.size(path);
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not read file size", exception);
    }
  }
}
