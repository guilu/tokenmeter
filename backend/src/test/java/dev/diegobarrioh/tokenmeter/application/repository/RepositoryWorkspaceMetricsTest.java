package dev.diegobarrioh.tokenmeter.application.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryWorkspaceMetricsTest {
  @TempDir Path tempDir;

  @Test
  void registersWorkspaceDiskGauges() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    RepositoryIntakeProperties properties =
        new RepositoryIntakeProperties(tempDir, 1024, Duration.ofSeconds(2));

    new RepositoryWorkspaceMetrics(properties, registry).registerGauges();

    Gauge usable =
        registry
            .find("tokenmeter.workspace.disk.usable.bytes")
            .tag("path", tempDir.toString())
            .gauge();
    Gauge free =
        registry
            .find("tokenmeter.workspace.disk.free.bytes")
            .tag("path", tempDir.toString())
            .gauge();
    Gauge total =
        registry
            .find("tokenmeter.workspace.disk.total.bytes")
            .tag("path", tempDir.toString())
            .gauge();

    assertThat(usable).isNotNull();
    assertThat(free).isNotNull();
    assertThat(total).isNotNull();
    assertThat(total.value()).isPositive();
    assertThat(usable.value()).isPositive();
    assertThat(usable.value()).isLessThanOrEqualTo(total.value());
  }

  @Test
  void gaugesFallBackToParentWhenWorkspaceMissing() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    Path missing = tempDir.resolve("not-yet-created");
    RepositoryIntakeProperties properties =
        new RepositoryIntakeProperties(missing, 1024, Duration.ofSeconds(2));

    new RepositoryWorkspaceMetrics(properties, registry).registerGauges();

    Gauge total =
        registry
            .find("tokenmeter.workspace.disk.total.bytes")
            .tag("path", missing.toString())
            .gauge();

    assertThat(total).isNotNull();
    assertThat(total.value()).isPositive();
  }
}
