package dev.diegobarrioh.tokenmeter.application.analyzer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FileLanguageDetector {
  private static final String UNKNOWN_LANGUAGE = "Unknown";
  private static final Map<String, String> EXTENSION_LANGUAGES =
      Map.ofEntries(
          Map.entry("java", "Java"),
          Map.entry("kt", "Kotlin"),
          Map.entry("kts", "Kotlin"),
          Map.entry("js", "JavaScript"),
          Map.entry("jsx", "JavaScript"),
          Map.entry("ts", "TypeScript"),
          Map.entry("tsx", "TypeScript"),
          Map.entry("md", "Markdown"),
          Map.entry("markdown", "Markdown"),
          Map.entry("yml", "YAML"),
          Map.entry("yaml", "YAML"),
          Map.entry("json", "JSON"),
          Map.entry("xml", "XML"),
          Map.entry("html", "HTML"),
          Map.entry("css", "CSS"),
          Map.entry("sql", "SQL"),
          Map.entry("sh", "Shell"),
          Map.entry("py", "Python"),
          Map.entry("go", "Go"),
          Map.entry("rs", "Rust"));

  public String detect(Path file) {
    String fileName = file.getFileName().toString();
    int extensionIndex = fileName.lastIndexOf('.');
    if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
      return UNKNOWN_LANGUAGE;
    }
    String extension = fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    return EXTENSION_LANGUAGES.getOrDefault(extension, UNKNOWN_LANGUAGE);
  }
}
