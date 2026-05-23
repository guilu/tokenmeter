package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.jobs;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisJobJpaRepository extends JpaRepository<AnalysisJobEntity, UUID> {

  List<AnalysisJobEntity> findByStatusIn(Collection<AnalysisJobStatus> statuses);

  List<AnalysisJobEntity> findByStatusAndCompletedAtBefore(
      AnalysisJobStatus status, Instant cutoff);

  int countByStatus(AnalysisJobStatus status);

  @Query(
      "select count(j) from AnalysisJobEntity j "
          + "where j.status = dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus.QUEUED "
          + "and (j.createdAt < :createdAt "
          + "     or (j.createdAt = :createdAt and j.id < :id))")
  int countQueuedAheadOf(@Param("createdAt") Instant createdAt, @Param("id") UUID id);
}
