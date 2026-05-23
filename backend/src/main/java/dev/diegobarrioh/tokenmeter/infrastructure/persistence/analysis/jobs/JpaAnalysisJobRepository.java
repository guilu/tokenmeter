package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.jobs;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisJobRepository;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAnalysisJobRepository implements AnalysisJobRepository {

  private final AnalysisJobJpaRepository delegate;

  public JpaAnalysisJobRepository(AnalysisJobJpaRepository delegate) {
    this.delegate = delegate;
  }

  @Override
  @Transactional
  public AnalysisJobSnapshot save(AnalysisJobSnapshot initialQueued) {
    AnalysisJobEntity entity = toNewEntity(initialQueued);
    AnalysisJobEntity persisted = delegate.save(entity);
    return toSnapshot(persisted);
  }

  @Override
  @Transactional
  public void markStarted(AnalysisJobId id, Instant when) {
    delegate
        .findById(id.value())
        .ifPresent(
            entity -> {
              if (entity.getStatus().isTerminal()) {
                return;
              }
              entity.setStatus(AnalysisJobStatus.RUNNING);
              if (entity.getStartedAt() == null) {
                entity.setStartedAt(when);
              }
            });
  }

  @Override
  @Transactional
  public void updatePhase(
      AnalysisJobId id, AnalysisJobPhase phase, int progressPercent, String message) {
    delegate
        .findById(id.value())
        .ifPresent(
            entity -> {
              if (entity.getStatus().isTerminal()) {
                return;
              }
              entity.setPhase(phase);
              entity.setProgressPercent((short) clampProgress(progressPercent));
              entity.setMessage(message);
            });
  }

  @Override
  @Transactional
  public void updateMetrics(AnalysisJobId id, AnalysisJobMetrics metrics) {
    if (metrics == null) {
      return;
    }
    delegate
        .findById(id.value())
        .ifPresent(
            entity -> {
              if (metrics.filesDiscovered() != null) {
                entity.setFilesDiscovered(metrics.filesDiscovered());
              }
              if (metrics.filesProcessed() != null) {
                entity.setFilesProcessed(metrics.filesProcessed());
              }
              if (metrics.filesSkipped() != null) {
                entity.setFilesSkipped(metrics.filesSkipped());
              }
              if (metrics.tokensCounted() != null) {
                entity.setTokensCounted(metrics.tokensCounted());
              }
              if (metrics.contextWindows() != null) {
                entity.setContextWindows(metrics.contextWindows());
              }
              if (metrics.pricingModelsProcessed() != null) {
                entity.setPricingModelsProcessed(metrics.pricingModelsProcessed());
              }
            });
  }

  @Override
  @Transactional
  public void markSuccess(
      AnalysisJobId id, UUID analysisId, AnalysisJobMetrics finalMetrics, Instant when) {
    delegate
        .findById(id.value())
        .ifPresent(
            entity -> {
              if (entity.getStatus().isTerminal()) {
                return;
              }
              applyMetricsMerge(entity, finalMetrics);
              entity.setAnalysisId(analysisId);
              entity.setPhase(AnalysisJobPhase.COMPLETED);
              entity.setStatus(AnalysisJobStatus.SUCCESS);
              entity.setProgressPercent((short) 100);
              entity.setCompletedAt(when);
              entity.setErrorCode(null);
              entity.setErrorMessage(null);
            });
  }

  @Override
  @Transactional
  public void markFailed(
      AnalysisJobId id, AnalysisJobErrorCode code, String message, Instant when) {
    delegate
        .findById(id.value())
        .ifPresent(
            entity -> {
              if (entity.getStatus().isTerminal()) {
                return;
              }
              entity.setStatus(AnalysisJobStatus.FAILED);
              entity.setPhase(AnalysisJobPhase.FAILED);
              entity.setErrorCode(
                  code == null ? AnalysisJobErrorCode.ANALYSIS_FAILED.name() : code.name());
              entity.setErrorMessage(
                  message == null || message.isBlank() ? "Analysis failed" : message);
              entity.setCompletedAt(when);
            });
  }

  @Override
  @Transactional
  public void markInterrupted(AnalysisJobId id, Instant when) {
    markFailed(
        id, AnalysisJobErrorCode.JOB_INTERRUPTED, "Analysis interrupted by service restart", when);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AnalysisJobSnapshot> findById(AnalysisJobId id) {
    return delegate.findById(id.value()).map(JpaAnalysisJobRepository::toSnapshot);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AnalysisJobSnapshot> findNonTerminal() {
    return delegate
        .findByStatusIn(List.of(AnalysisJobStatus.QUEUED, AnalysisJobStatus.RUNNING))
        .stream()
        .map(JpaAnalysisJobRepository::toSnapshot)
        .toList();
  }

  @Override
  @Transactional
  public int deleteCompletedBefore(AnalysisJobStatus status, Instant cutoff) {
    List<AnalysisJobEntity> stale = delegate.findByStatusAndCompletedAtBefore(status, cutoff);
    delegate.deleteAll(stale);
    return stale.size();
  }

  @Override
  @Transactional
  public void deleteById(UUID id) {
    delegate.deleteById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public int countByStatus(AnalysisJobStatus status) {
    return delegate.countByStatus(status);
  }

  @Override
  @Transactional(readOnly = true)
  public int countQueuedAheadOf(AnalysisJobId targetId) {
    return delegate
        .findById(targetId.value())
        .map(
            entity -> {
              if (entity.getStatus() != AnalysisJobStatus.QUEUED) {
                return 0;
              }
              return delegate.countQueuedAheadOf(entity.getCreatedAt(), entity.getId()) + 1;
            })
        .orElse(0);
  }

  private static int clampProgress(int progressPercent) {
    if (progressPercent < 0) {
      return 0;
    }
    if (progressPercent > 99) {
      return 99;
    }
    return progressPercent;
  }

  private static AnalysisJobEntity toNewEntity(AnalysisJobSnapshot snapshot) {
    AnalysisJobEntity entity =
        new AnalysisJobEntity(
            snapshot.id().value(),
            snapshot.repositoryUrl(),
            snapshot.status(),
            snapshot.phase(),
            (short) snapshot.progressPercent(),
            snapshot.createdAt(),
            snapshot.updatedAt());
    entity.setMessage(snapshot.message());
    entity.setStartedAt(snapshot.startedAt());
    entity.setCompletedAt(snapshot.completedAt());
    entity.setAnalysisId(snapshot.analysisId());
    if (snapshot.errorCode() != null) {
      entity.setErrorCode(snapshot.errorCode().name());
    }
    entity.setErrorMessage(snapshot.errorMessage());
    applyMetricsMerge(entity, snapshot.metrics());
    return entity;
  }

  private static void applyMetricsMerge(AnalysisJobEntity entity, AnalysisJobMetrics metrics) {
    if (metrics == null) {
      return;
    }
    if (metrics.filesDiscovered() != null) {
      entity.setFilesDiscovered(metrics.filesDiscovered());
    }
    if (metrics.filesProcessed() != null) {
      entity.setFilesProcessed(metrics.filesProcessed());
    }
    if (metrics.filesSkipped() != null) {
      entity.setFilesSkipped(metrics.filesSkipped());
    }
    if (metrics.tokensCounted() != null) {
      entity.setTokensCounted(metrics.tokensCounted());
    }
    if (metrics.contextWindows() != null) {
      entity.setContextWindows(metrics.contextWindows());
    }
    if (metrics.pricingModelsProcessed() != null) {
      entity.setPricingModelsProcessed(metrics.pricingModelsProcessed());
    }
  }

  static AnalysisJobSnapshot toSnapshot(AnalysisJobEntity entity) {
    AnalysisJobMetrics metrics =
        new AnalysisJobMetrics(
            entity.getFilesDiscovered(),
            entity.getFilesProcessed(),
            entity.getFilesSkipped(),
            entity.getTokensCounted(),
            entity.getContextWindows(),
            entity.getPricingModelsProcessed());
    AnalysisJobErrorCode errorCode =
        entity.getErrorCode() == null ? null : AnalysisJobErrorCode.valueOf(entity.getErrorCode());
    return new AnalysisJobSnapshot(
        new AnalysisJobId(entity.getId()),
        entity.getRepositoryUrl(),
        entity.getStatus(),
        entity.getPhase(),
        entity.getProgressPercent(),
        entity.getMessage(),
        entity.getAnalysisId(),
        errorCode,
        entity.getErrorMessage(),
        metrics,
        entity.getCreatedAt(),
        entity.getStartedAt(),
        entity.getUpdatedAt(),
        entity.getCompletedAt());
  }
}
