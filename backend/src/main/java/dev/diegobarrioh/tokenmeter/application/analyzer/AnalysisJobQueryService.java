package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Read-only access to an {@code AnalysisJob} snapshot, used by the polling endpoint. */
@Service
public class AnalysisJobQueryService {

  private final AnalysisJobRepository jobRepository;

  public AnalysisJobQueryService(AnalysisJobRepository jobRepository) {
    this.jobRepository = jobRepository;
  }

  public Optional<AnalysisJobSnapshot> findById(AnalysisJobId id) {
    return jobRepository.findById(id);
  }
}
