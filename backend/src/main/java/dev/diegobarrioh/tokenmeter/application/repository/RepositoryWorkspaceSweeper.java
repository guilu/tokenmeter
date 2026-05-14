package dev.diegobarrioh.tokenmeter.application.repository;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RepositoryWorkspaceSweeper {
  private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWorkspaceSweeper.class);

  private final RepositoryIntakeProperties properties;

  public RepositoryWorkspaceSweeper(RepositoryIntakeProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  public void sweep() {
    Path root = properties.tempDirectory();
    try {
      Files.createDirectories(root);
    } catch (IOException exception) {
      LOGGER.warn("Could not create workspace directory {} for sweep", root, exception);
      return;
    }
    int deleted = 0;
    int failed = 0;
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        if (deleteRecursively(entry)) {
          deleted++;
        } else {
          failed++;
        }
      }
    } catch (IOException exception) {
      LOGGER.warn("Could not enumerate workspace directory {}", root, exception);
      return;
    }
    if (deleted > 0 || failed > 0) {
      LOGGER.info(
          "Workspace sweep at {} removed {} leftover entries ({} failures)", root, deleted, failed);
    }
  }

  private boolean deleteRecursively(Path path) {
    if (!Files.exists(path)) {
      return false;
    }
    try (var paths = Files.walk(path)) {
      paths.sorted(Comparator.reverseOrder()).forEach(RepositoryWorkspaceSweeper::deleteIfExists);
      return !Files.exists(path);
    } catch (IOException | UncheckedIOException exception) {
      LOGGER.warn("Could not delete leftover path {}", path, exception);
      return false;
    }
  }

  private static void deleteIfExists(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not delete leftover path " + path, exception);
    }
  }
}
