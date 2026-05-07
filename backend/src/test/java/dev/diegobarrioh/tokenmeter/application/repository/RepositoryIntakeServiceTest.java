package dev.diegobarrioh.tokenmeter.application.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryIntakeServiceTest {
  @TempDir Path tempDir;

  @Test
  void clonesRepositoryIntoTemporaryDirectoryAndCleansItAfterSuccess() {
    RepositoryIntakeService service =
        new RepositoryIntakeService(
            (repositoryUrl, targetDirectory) -> writeFile(targetDirectory, "README.md", "hello"),
            properties(1024, Duration.ofSeconds(2)));

    RepositoryIntakeResult result = service.intake("https://github.com/guilu/tokenmeter");

    assertThat(result.repositoryUrl()).isEqualTo("https://github.com/guilu/tokenmeter");
    assertThat(result.cloneUrl()).isEqualTo("https://github.com/guilu/tokenmeter.git");
    assertThat(result.totalBytes()).isEqualTo(5);
    assertThat(result.fileCount()).isEqualTo(1);
    assertThat(result.cleanedUp()).isTrue();
    assertThat(tempDir).isEmptyDirectory();
  }

  @Test
  void cleansTemporaryDirectoryAfterFailedClone() {
    RepositoryIntakeService service =
        new RepositoryIntakeService(
            (repositoryUrl, targetDirectory) -> {
              writeFile(targetDirectory, "partial.txt", "partial");
              throw new RepositoryIntakeException(
                  RepositoryIntakeErrorCode.REPOSITORY_NOT_ACCESSIBLE, "not accessible");
            },
            properties(1024, Duration.ofSeconds(2)));

    assertThatThrownBy(() -> service.intake("https://github.com/guilu/private-repo"))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.REPOSITORY_NOT_ACCESSIBLE);
    assertThat(tempDir).isEmptyDirectory();
  }

  @Test
  void enforcesRepositorySizeLimit() {
    RepositoryIntakeService service =
        new RepositoryIntakeService(
            (repositoryUrl, targetDirectory) ->
                writeFile(targetDirectory, "large.txt", "too large"),
            properties(3, Duration.ofSeconds(2)));

    assertThatThrownBy(() -> service.intake("https://github.com/guilu/tokenmeter"))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.REPOSITORY_TOO_LARGE);
    assertThat(tempDir).isEmptyDirectory();
  }

  @Test
  void timesOutSlowCloneAndCleansDirectory() {
    RepositoryIntakeService service =
        new RepositoryIntakeService(
            (repositoryUrl, targetDirectory) -> sleep(Duration.ofMillis(500)),
            properties(1024, Duration.ofMillis(50)));

    assertThatThrownBy(() -> service.intake("https://github.com/guilu/tokenmeter"))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.CLONE_TIMEOUT);
    assertThat(tempDir).isEmptyDirectory();
  }

  private RepositoryIntakeProperties properties(long maxBytes, Duration timeout) {
    return new RepositoryIntakeProperties(tempDir, maxBytes, timeout);
  }

  private static void writeFile(Path directory, String fileName, String content) {
    try {
      Files.createDirectories(directory);
      Files.writeString(directory.resolve(fileName), content);
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
