package dev.diegobarrioh.tokenmeter.application.analyzer;

import java.util.Optional;
import java.util.UUID;

public interface AnalysisPersistenceService {
  RepositoryAnalysisResult save(RepositoryAnalysisResult result);

  Optional<RepositoryAnalysisResult> findById(UUID id);

  /**
   * Returns the id of the most recent SUCCESS analysis stored for the given repository URL under
   * the same {@code pricingSnapshotId}. Used by the submission front door to short-circuit
   * duplicate work — if a prior analysis matches, the submitted job can skip the pipeline and point
   * at the cached row.
   */
  Optional<UUID> findLatestSuccessIdFor(String repositoryUrl, String pricingSnapshotId);

  /**
   * Returns the id of the most recent SUCCESS analysis for the given repository URL, regardless of
   * pricing snapshot. Used by the by-repo badge endpoint to display the latest result for any
   * repository URL.
   */
  Optional<UUID> findLatestSuccessIdFor(String repositoryUrl);
}
