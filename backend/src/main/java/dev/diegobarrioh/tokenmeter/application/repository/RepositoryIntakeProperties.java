package dev.diegobarrioh.tokenmeter.application.repository;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tokenmeter.repository-intake")
public record RepositoryIntakeProperties(
    Path tempDirectory, long maxRepositoryBytes, Duration cloneTimeout) {
  public RepositoryIntakeProperties {
    if (tempDirectory == null) {
      tempDirectory = Path.of(System.getProperty("java.io.tmpdir"), "tokenmeter-repositories");
    }
    if (maxRepositoryBytes <= 0) {
      maxRepositoryBytes = 100L * 1024L * 1024L;
    }
    if (cloneTimeout == null || cloneTimeout.isNegative() || cloneTimeout.isZero()) {
      cloneTimeout = Duration.ofSeconds(30);
    }
  }
}
