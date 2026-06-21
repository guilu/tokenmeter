package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import dev.diegobarrioh.tokenmeter.application.repository.AnalyzedRepositoriesPort;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link AnalyzedRepositoriesPort}. Resolves the analyzed subset with a single
 * {@code IN} query against {@code analysis}, short-circuiting empty input so no query runs for a
 * trending list whose URLs all failed to normalize.
 */
@Component
public class JpaAnalyzedRepositoriesAdapter implements AnalyzedRepositoriesPort {

  private final AnalysisJpaRepository repository;

  public JpaAnalyzedRepositoriesAdapter(AnalysisJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public Set<String> analyzedRepositoryUrls(Collection<String> normalizedUrls) {
    if (normalizedUrls == null || normalizedUrls.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(repository.findAnalyzedRepositoryUrls(normalizedUrls));
  }
}
