package dev.diegobarrioh.tokenmeter.application.repository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.ToDoubleFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RepositoryWorkspaceMetrics {
  private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWorkspaceMetrics.class);

  private final RepositoryIntakeProperties properties;
  private final MeterRegistry meterRegistry;

  public RepositoryWorkspaceMetrics(
      RepositoryIntakeProperties properties, MeterRegistry meterRegistry) {
    this.properties = properties;
    this.meterRegistry = meterRegistry;
  }

  @PostConstruct
  public void registerGauges() {
    Tags tags = Tags.of("path", properties.tempDirectory().toString());
    register(
        "tokenmeter.workspace.disk.usable.bytes",
        "Bytes available to the JVM on the filesystem hosting the workspace directory",
        tags,
        FileStore::getUsableSpace);
    register(
        "tokenmeter.workspace.disk.free.bytes",
        "Unallocated bytes on the filesystem hosting the workspace directory",
        tags,
        FileStore::getUnallocatedSpace);
    register(
        "tokenmeter.workspace.disk.total.bytes",
        "Total bytes of the filesystem hosting the workspace directory",
        tags,
        FileStore::getTotalSpace);
  }

  private void register(
      String name, String description, Tags tags, FileStoreLongAccessor accessor) {
    Gauge.builder(name, this, sampler(accessor))
        .description(description)
        .baseUnit("bytes")
        .tags(tags)
        .strongReference(true)
        .register(meterRegistry);
  }

  private ToDoubleFunction<RepositoryWorkspaceMetrics> sampler(FileStoreLongAccessor accessor) {
    return self -> {
      try {
        FileStore store = fileStore();
        return store == null ? Double.NaN : (double) accessor.read(store);
      } catch (IOException exception) {
        LOGGER.debug("Could not sample workspace filesystem metric", exception);
        return Double.NaN;
      }
    };
  }

  private FileStore fileStore() throws IOException {
    Path path = properties.tempDirectory();
    Path probe = path;
    while (probe != null && !Files.exists(probe)) {
      probe = probe.getParent();
    }
    return probe == null ? null : Files.getFileStore(probe);
  }

  @FunctionalInterface
  private interface FileStoreLongAccessor {
    long read(FileStore store) throws IOException;
  }
}
