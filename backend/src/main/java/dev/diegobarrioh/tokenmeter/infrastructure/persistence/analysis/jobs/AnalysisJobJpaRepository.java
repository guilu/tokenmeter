package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.jobs;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJobJpaRepository extends JpaRepository<AnalysisJobEntity, UUID> {

  List<AnalysisJobEntity> findByStatusIn(Collection<AnalysisJobStatus> statuses);

  List<AnalysisJobEntity> findByStatusAndCompletedAtBefore(
      AnalysisJobStatus status, Instant cutoff);
}
