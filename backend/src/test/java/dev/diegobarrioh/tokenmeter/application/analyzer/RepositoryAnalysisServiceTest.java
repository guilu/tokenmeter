package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepositoryAnalysisServiceTest {

  @Mock private AnalysisPersistenceService persistence;

  @InjectMocks private RepositoryAnalysisService service;

  @Test
  void findLatestByRepositoryUrl_returnsPresentWhenPersistenceResolvesId() {
    UUID id = UUID.randomUUID();
    RepositoryAnalysisResult result = sampleResult(id);
    when(persistence.findLatestSuccessIdFor("https://github.com/acme/myrepo"))
        .thenReturn(Optional.of(id));
    when(persistence.findById(id)).thenReturn(Optional.of(result));

    Optional<RepositoryAnalysisResult> found =
        service.findLatestByRepositoryUrl("https://github.com/acme/myrepo");

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(id);
    assertThat(found.get().repositoryUrl()).isEqualTo("https://github.com/acme/myrepo");
  }

  @Test
  void findLatestByRepositoryUrl_returnsEmptyWhenPersistenceReturnsEmpty() {
    when(persistence.findLatestSuccessIdFor("https://github.com/acme/unknown"))
        .thenReturn(Optional.empty());

    Optional<RepositoryAnalysisResult> found =
        service.findLatestByRepositoryUrl("https://github.com/acme/unknown");

    assertThat(found).isEmpty();
  }

  @Test
  void findById_throwsAnalysisNotFoundForUnknownId() {
    UUID id = UUID.randomUUID();
    when(persistence.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById(id)).isInstanceOf(AnalysisNotFoundException.class);
  }

  private static RepositoryAnalysisResult sampleResult(UUID id) {
    return new RepositoryAnalysisResult(
        id,
        Instant.parse("2026-06-01T10:00:00Z"),
        "https://github.com/acme/myrepo",
        "https://github.com/acme/myrepo.git",
        "acme",
        "myrepo",
        new RepositoryScanResult(1, 10, 100, List.of(), Map.of()),
        new RepositoryTokenizationResult("o200k_base", 1, 100, Map.of(), List.of(), Map.of()),
        List.of());
  }
}
