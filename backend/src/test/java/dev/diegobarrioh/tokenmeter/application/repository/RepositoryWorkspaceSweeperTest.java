package dev.diegobarrioh.tokenmeter.application.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryWorkspaceSweeperTest {
  @TempDir Path tempDir;

  @Test
  void removesLeftoverCloneDirectoriesOnStartup() throws IOException {
    Path orphan = Files.createDirectory(tempDir.resolve("owner-repo-abc123"));
    Files.writeString(orphan.resolve("README.md"), "leftover");
    Files.createDirectory(orphan.resolve("nested"));
    Files.writeString(orphan.resolve("nested/file.txt"), "still leftover");

    new RepositoryWorkspaceSweeper(properties()).sweep();

    assertThat(tempDir).isEmptyDirectory();
  }

  @Test
  void createsWorkspaceDirectoryWhenMissing() throws IOException {
    Path missing = tempDir.resolve("not-yet-created");
    RepositoryIntakeProperties properties =
        new RepositoryIntakeProperties(missing, 1024, Duration.ofSeconds(2));

    new RepositoryWorkspaceSweeper(properties).sweep();

    assertThat(Files.isDirectory(missing)).isTrue();
  }

  @Test
  void doesNothingWhenWorkspaceIsAlreadyEmpty() {
    new RepositoryWorkspaceSweeper(properties()).sweep();

    assertThat(tempDir).isEmptyDirectory();
  }

  private RepositoryIntakeProperties properties() {
    return new RepositoryIntakeProperties(tempDir, 1024, Duration.ofSeconds(2));
  }
}
