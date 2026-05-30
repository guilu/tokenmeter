package dev.diegobarrioh.tokenmeter.application.tokenizer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.analyzer.BinaryFileDetector;
import dev.diegobarrioh.tokenmeter.application.analyzer.FileLanguageDetector;
import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryFileScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryTokenizationServiceTest {
  @TempDir Path repositoryRoot;

  private static final TokenizationProgressListener NO_OP = (p, t, tok) -> {};

  private final OpenAiTokenCounter tokenCounter = new OpenAiTokenCounter();
  private final RepositoryFileScanner scanner =
      new RepositoryFileScanner(new FileLanguageDetector(), new BinaryFileDetector());
  private final RepositoryTokenizationService tokenizationService =
      new RepositoryTokenizationService(tokenCounter);

  @Test
  void generatesTokenMetricsPerFileAndLanguage() throws IOException {
    write("src/App.java", "public class App {}\n");
    write("web/App.tsx", "export const App = () => null;\n");
    write("README.md", "# TokenMeter\n\nCounts tokens.\n");
    write("config/app.yml", "app:\n  enabled: true\n");
    write("package.json", "{\"name\":\"tokenmeter\"}\n");

    var scan = scanner.scan(repositoryRoot);
    var result = tokenizationService.tokenize(repositoryRoot, scan, NO_OP);

    assertThat(result.encoding()).isEqualTo("o200k_base");
    assertThat(result.totalFiles()).isEqualTo(5);
    assertThat(result.files()).hasSize(5);
    assertThat(result.files()).allSatisfy(file -> assertThat(file.tokens()).isPositive());
    assertThat(result.languages().get("Java").tokens()).isPositive();
    assertThat(result.languages().get("TypeScript").tokens()).isPositive();
    assertThat(result.languages().get("Markdown").tokens()).isPositive();
    assertThat(result.languages().get("YAML").tokens()).isPositive();
    assertThat(result.languages().get("JSON").tokens()).isPositive();
  }

  @Test
  void totalTokensEqualSumOfPerFileAndPerLanguageTokens() throws IOException {
    write("src/App.java", "public class App {}\n");
    write("src/Other.java", "public class Other {}\n");
    write("web/App.ts", "const answer = 42;\n");

    var result = tokenizationService.tokenize(repositoryRoot, scanner.scan(repositoryRoot), NO_OP);

    assertThat(result.totalTokens())
        .isEqualTo(result.files().stream().mapToLong(file -> file.tokens()).sum());
    assertThat(result.totalTokens())
        .isEqualTo(
            result.languages().values().stream().mapToLong(language -> language.tokens()).sum());
  }

  @Test
  void tokenizesEmptyFilesAsZeroTokens() throws IOException {
    write("empty.md", "");

    var result = tokenizationService.tokenize(repositoryRoot, scanner.scan(repositoryRoot), NO_OP);

    assertThat(result.totalTokens()).isZero();
    assertThat(result.files().getFirst().tokens()).isZero();
    assertThat(result.languages().get("Markdown").tokens()).isZero();
  }

  @Test
  void tokenizesLargeFilesWithChunkedStreaming() throws IOException {
    String line = "public class Large { private String value = \"hello world\"; }\n";
    write("src/Large.java", line.repeat(5000));

    var result = tokenizationService.tokenize(repositoryRoot, scanner.scan(repositoryRoot), NO_OP);

    assertThat(result.totalFiles()).isEqualTo(1);
    assertThat(result.totalTokens()).isPositive();
    assertThat(result.languages().get("Java").tokens()).isEqualTo(result.totalTokens());
  }

  @Test
  void performanceRemainsAcceptableForMediumRepository() throws IOException {
    for (int index = 0; index < 750; index++) {
      write("src/File%03d.java".formatted(index), "public class File%03d {}\n".formatted(index));
    }

    long startedAt = System.nanoTime();
    var result = tokenizationService.tokenize(repositoryRoot, scanner.scan(repositoryRoot), NO_OP);
    long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

    assertThat(result.totalFiles()).isEqualTo(750);
    assertThat(result.totalTokens()).isPositive();
    assertThat(elapsedMillis).isLessThan(5_000);
  }

  @Test
  void listenerInvokedOncePerFileWithCumulativeValues() throws IOException {
    write("src/A.java", "public class A {}\n");
    write("src/B.java", "public class B {}\n");
    write("src/C.java", "public class C {}\n");

    record ProgressCall(long filesProcessed, long totalFiles, long tokensSoFar) {}
    List<ProgressCall> calls = new ArrayList<>();
    TokenizationProgressListener capturingListener =
        (filesProcessed, totalFiles, tokensSoFar) ->
            calls.add(new ProgressCall(filesProcessed, totalFiles, tokensSoFar));

    var scan = scanner.scan(repositoryRoot);
    var result = tokenizationService.tokenize(repositoryRoot, scan, capturingListener);

    assertThat(calls).hasSize(3);
    assertThat(calls.get(0).filesProcessed()).isEqualTo(1L);
    assertThat(calls.get(1).filesProcessed()).isEqualTo(2L);
    assertThat(calls.get(2).filesProcessed()).isEqualTo(3L);
    calls.forEach(call -> assertThat(call.totalFiles()).isEqualTo(3L));
    assertThat(calls.get(2).tokensSoFar()).isEqualTo(result.totalTokens());
    // tokensSoFar must be strictly non-decreasing
    for (int i = 1; i < calls.size(); i++) {
      assertThat(calls.get(i).tokensSoFar()).isGreaterThanOrEqualTo(calls.get(i - 1).tokensSoFar());
    }
  }

  @Test
  void zeroFileRepoNeverInvokesListener() throws IOException {
    // empty temp dir — no files written, scanner finds nothing
    var scan = scanner.scan(repositoryRoot);
    boolean[] listenerCalled = {false};
    TokenizationProgressListener listener = (p, t, tok) -> listenerCalled[0] = true;

    var result = tokenizationService.tokenize(repositoryRoot, scan, listener);

    assertThat(listenerCalled[0]).isFalse();
    assertThat(result.totalFiles()).isZero();
    assertThat(result.totalTokens()).isZero();
  }

  private void write(String relativePath, String content) throws IOException {
    Path target = repositoryRoot.resolve(relativePath);
    Files.createDirectories(target.getParent());
    Files.writeString(target, content);
  }
}
