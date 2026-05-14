package dev.diegobarrioh.tokenmeter.application.repository;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RepositoryWorkspaceSweeper {
  private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWorkspaceSweeper.class);

  // Matches directories produced by createCloneDirectory: {owner}-{name}-{random}
  // Requires at least two hyphen-separated lowercase segments followed by a trailing hyphen.
  static final Pattern CLONE_DIR_PATTERN =
      Pattern.compile("^[a-z0-9][a-z0-9_.-]*-[a-z0-9][a-z0-9_.-]*-");

  private final RepositoryIntakeProperties properties;

  public RepositoryWorkspaceSweeper(RepositoryIntakeProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  public void sweep() {
    Path root = properties.tempDirectory();
    LOGGER.info("Workspace sweep starting at {}", root);
    try {
      Files.createDirectories(root);
    } catch (IOException exception) {
      LOGGER.warn("Could not create workspace directory {} for sweep", root, exception);
      return;
    }
    int deleted = 0;
    int skipped = 0;
    int failed = 0;
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
      for (Path entry : entries) {
        if (!isCloneDirectory(entry)) {
          LOGGER.warn("Skipping unexpected workspace entry during sweep: {}", entry.getFileName());
          skipped++;
          continue;
        }
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
          "Workspace sweep at {} removed {} leftover clone directories ({} failures, {} skipped)",
          root,
          deleted,
          failed,
          skipped);
    }
  }

  static boolean isCloneDirectory(Path entry) {
    return Files.isDirectory(entry)
        && !Files.isSymbolicLink(entry)
        && CLONE_DIR_PATTERN.matcher(entry.getFileName().toString()).find();
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
