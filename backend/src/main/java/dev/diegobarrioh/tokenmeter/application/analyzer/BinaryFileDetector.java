package dev.diegobarrioh.tokenmeter.application.analyzer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BinaryFileDetector {
  private static final int SAMPLE_BYTES = 8192;
  private static final Set<String> BINARY_EXTENSIONS =
      Set.of(
          "7z", "class", "dll", "dmg", "exe", "gif", "gz", "ico", "jar", "jpeg", "jpg", "pdf",
          "png", "so", "tar", "war", "webp", "zip");

  public boolean isBinary(Path file) throws IOException {
    if (hasBinaryExtension(file)) {
      return true;
    }
    byte[] buffer = new byte[SAMPLE_BYTES];
    try (InputStream inputStream = Files.newInputStream(file)) {
      int bytesRead = inputStream.read(buffer);
      if (bytesRead <= 0) {
        return false;
      }
      for (int index = 0; index < bytesRead; index++) {
        if (buffer[index] == 0) {
          return true;
        }
      }
      return false;
    }
  }

  private boolean hasBinaryExtension(Path file) {
    String fileName = file.getFileName().toString();
    int extensionIndex = fileName.lastIndexOf('.');
    if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
      return false;
    }
    String extension = fileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    return BINARY_EXTENSIONS.contains(extension);
  }
}
