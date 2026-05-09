package dev.diegobarrioh.tokenmeter.application.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositorySizeCalculatorTest {
  @TempDir Path tempDir;

  @Test
  void ignoresGitMetadataWhenSummarizingRepositorySize() throws IOException {
    writeFile("src/App.java", "class App {}\n");
    writeFile(".git/objects/pack/pack-file.pack", "large git metadata");

    var summary = new RepositorySizeCalculator().summarize(tempDir);

    assertThat(summary.totalBytes()).isEqualTo("class App {}\n".getBytes().length);
    assertThat(summary.fileCount()).isEqualTo(1);
  }

  private void writeFile(String relativePath, String content) throws IOException {
    Path file = tempDir.resolve(relativePath);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
