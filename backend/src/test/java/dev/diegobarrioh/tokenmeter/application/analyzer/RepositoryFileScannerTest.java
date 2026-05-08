package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryFileScannerTest {
  @TempDir Path repositoryRoot;

  private RepositoryFileScanner scanner;

  @BeforeEach
  void setUp() {
    scanner = new RepositoryFileScanner(new FileLanguageDetector(), new BinaryFileDetector());
  }

  @Test
  void scansRepositoryFilesRecursivelyAndCalculatesTotals() throws IOException {
    write("src/main/java/App.java", "class App {\n}\n");
    write("frontend/src/App.tsx", "export const App = () => null\n");
    write("README.md", "# TokenMeter\n\nhello\n");

    var result = scanner.scan(repositoryRoot);

    assertThat(result.totalFiles()).isEqualTo(3);
    assertThat(result.totalLines()).isEqualTo(6);
    assertThat(result.totalBytes()).isEqualTo(64);
    assertThat(result.files())
        .extracting(metric -> metric.relativePath().toString())
        .containsExactly("README.md", "frontend/src/App.tsx", "src/main/java/App.java");
  }

  @Test
  void ignoresKnownWasteDirectories() throws IOException {
    write("src/App.java", "class App {}\n");
    write("node_modules/lib/index.js", "ignored\n");
    write(".git/config", "ignored\n");
    write("target/generated/Generated.java", "ignored\n");
    write("build/tmp/output.txt", "ignored\n");
    write("dist/index.js", "ignored\n");
    write("coverage/lcov.info", "ignored\n");

    var result = scanner.scan(repositoryRoot);

    assertThat(result.totalFiles()).isEqualTo(1);
    assertThat(result.files().getFirst().relativePath().toString()).isEqualTo("src/App.java");
  }

  @Test
  void excludesBinaryFilesByExtensionAndContent() throws IOException {
    write("src/App.java", "class App {}\n");
    writeBytes("docs/logo.png", new byte[] {1, 2, 3});
    writeBytes("data/raw.bin", new byte[] {'a', 0, 'b'});

    var result = scanner.scan(repositoryRoot);

    assertThat(result.totalFiles()).isEqualTo(1);
    assertThat(result.files().getFirst().relativePath().toString()).isEqualTo("src/App.java");
  }

  @Test
  void detectsLanguagesByExtensionAndGroupsStatistics() throws IOException {
    write("src/App.java", "class App {}\n");
    write("web/main.ts", "type User = { id: string }\n");
    write("web/component.tsx", "export function Component() {\n  return null\n}\n");
    write("README.md", "# Docs\n");
    write("LICENSE", "plain text\n");

    var result = scanner.scan(repositoryRoot);

    assertThat(result.languages().get("Java")).hasFieldOrPropertyWithValue("files", 1L);
    assertThat(result.languages().get("TypeScript")).hasFieldOrPropertyWithValue("files", 2L);
    assertThat(result.languages().get("Markdown")).hasFieldOrPropertyWithValue("files", 1L);
    assertThat(result.languages().get("Unknown")).hasFieldOrPropertyWithValue("files", 1L);
    assertThat(result.languages().get("TypeScript").lines()).isEqualTo(4);
  }

  @Test
  void handlesEmptyFilesAndUnknownExtensionsSafely() throws IOException {
    write("empty.xyz", "");

    var result = scanner.scan(repositoryRoot);

    assertThat(result.totalFiles()).isEqualTo(1);
    assertThat(result.totalLines()).isZero();
    assertThat(result.totalBytes()).isZero();
    assertThat(result.files().getFirst().language()).isEqualTo("Unknown");
  }

  @Test
  void handlesLargeRepositories() throws IOException {
    for (int index = 0; index < 500; index++) {
      write("src/file-%03d.java".formatted(index), "class File%03d {}\n".formatted(index));
    }

    var result = scanner.scan(repositoryRoot);

    assertThat(result.totalFiles()).isEqualTo(500);
    assertThat(result.totalLines()).isEqualTo(500);
    assertThat(result.languages().get("Java").files()).isEqualTo(500);
  }

  @Test
  void rejectsNonDirectoryRoots() throws IOException {
    Path file = repositoryRoot.resolve("README.md");
    Files.writeString(file, "content");

    assertThatThrownBy(() -> scanner.scan(file)).isInstanceOf(IllegalArgumentException.class);
  }

  private void write(String relativePath, String content) throws IOException {
    Path target = repositoryRoot.resolve(relativePath);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
  }

  private void writeBytes(String relativePath, byte[] content) throws IOException {
    Path target = repositoryRoot.resolve(relativePath);
    Files.createDirectories(target.getParent());
    Files.write(target, content);
  }
}
