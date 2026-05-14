package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryFileMetric;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RepositoryFileScanner {
  private static final Set<String> IGNORED_DIRECTORIES =
      Set.of(".git", "node_modules", "target", "build", "dist", "coverage");

  private final FileLanguageDetector languageDetector;
  private final BinaryFileDetector binaryFileDetector;

  public RepositoryFileScanner(
      FileLanguageDetector languageDetector, BinaryFileDetector binaryFileDetector) {
    this.languageDetector = languageDetector;
    this.binaryFileDetector = binaryFileDetector;
  }

  public RepositoryScanResult scan(Path repositoryRoot) {
    if (!Files.isDirectory(repositoryRoot)) {
      throw new IllegalArgumentException("Repository root must be an existing directory");
    }

    List<RepositoryFileMetric> files = new ArrayList<>();
    try {
      Files.walkFileTree(repositoryRoot, new ScanningVisitor(repositoryRoot, files));
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not scan repository " + repositoryRoot, exception);
    }

    files.sort(Comparator.comparing(metric -> metric.relativePath().toString()));
    return summarize(files);
  }

  private RepositoryScanResult summarize(List<RepositoryFileMetric> files) {
    long totalLines = files.stream().mapToLong(RepositoryFileMetric::lines).sum();
    long totalBytes = files.stream().mapToLong(RepositoryFileMetric::bytes).sum();
    Map<String, MutableLanguageStatistics> mutableLanguages = new LinkedHashMap<>();

    files.forEach(
        file ->
            mutableLanguages
                .computeIfAbsent(file.language(), MutableLanguageStatistics::new)
                .add(file.lines(), file.bytes()));

    Map<String, LanguageStatistics> languages = new LinkedHashMap<>();
    mutableLanguages.forEach(
        (language, statistics) -> languages.put(language, statistics.toRecord()));

    return new RepositoryScanResult(
        files.size(), totalLines, totalBytes, List.copyOf(files), Map.copyOf(languages));
  }

  private final class ScanningVisitor extends SimpleFileVisitor<Path> {
    private final Path repositoryRoot;
    private final List<RepositoryFileMetric> files;

    private ScanningVisitor(Path repositoryRoot, List<RepositoryFileMetric> files) {
      this.repositoryRoot = repositoryRoot;
      this.files = files;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
      if (!repositoryRoot.equals(directory) && isIgnoredDirectory(directory)) {
        return FileVisitResult.SKIP_SUBTREE;
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
      if (attributes.isRegularFile() && !binaryFileDetector.isBinary(file)) {
        try {
          files.add(toMetric(file, attributes));
        } catch (MalformedInputException ignored) {
          // non-UTF-8 encoding slipped past binary detector — skip
        }
      }
      return FileVisitResult.CONTINUE;
    }

    private boolean isIgnoredDirectory(Path directory) {
      return IGNORED_DIRECTORIES.contains(
          directory.getFileName().toString().toLowerCase(Locale.ROOT));
    }

    private RepositoryFileMetric toMetric(Path file, BasicFileAttributes attributes)
        throws IOException {
      Path relativePath = repositoryRoot.relativize(file);
      return new RepositoryFileMetric(
          relativePath, languageDetector.detect(file), countLines(file), attributes.size());
    }
  }

  private static long countLines(Path file) throws IOException {
    long lines = 0;
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      while (reader.readLine() != null) {
        lines++;
      }
    }
    return lines;
  }

  private static final class MutableLanguageStatistics {
    private final String language;
    private long files;
    private long lines;
    private long bytes;

    private MutableLanguageStatistics(String language) {
      this.language = language;
    }

    private void add(long fileLines, long fileBytes) {
      files++;
      lines += fileLines;
      bytes += fileBytes;
    }

    private LanguageStatistics toRecord() {
      return new LanguageStatistics(language, files, lines, bytes);
    }
  }
}
