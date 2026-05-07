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
          paths.filter(Files::isRegularFile).map(RepositorySizeCalculator::fileSize).toList();
      long totalBytes = files.stream().mapToLong(Long::longValue).sum();
      return new RepositoryCloneSummary(totalBytes, files.size());
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not inspect cloned repository", exception);
    }
  }

  private static long fileSize(Path path) {
    try {
      return Files.size(path);
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not read file size", exception);
    }
  }
}
