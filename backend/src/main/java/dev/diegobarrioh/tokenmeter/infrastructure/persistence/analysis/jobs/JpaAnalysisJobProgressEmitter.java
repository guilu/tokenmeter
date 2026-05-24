package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.jobs;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisJobProgressEmitter;
import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisJobRepository;
import dev.diegobarrioh.tokenmeter.application.analyzer.MdcScope;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaAnalysisJobProgressEmitter implements AnalysisJobProgressEmitter {

  private static final Logger LOGGER = LoggerFactory.getLogger(JpaAnalysisJobProgressEmitter.class);

  private final AnalysisJobRepository jobRepository;
  private final Clock clock;

  public JpaAnalysisJobProgressEmitter(AnalysisJobRepository jobRepository, Clock clock) {
    this.jobRepository = jobRepository;
    this.clock = clock;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void transition(
      AnalysisJobId id, AnalysisJobPhase phase, int progressPercent, String message) {
    int clamped = clampProgress(progressPercent);
    try (MdcScope ignored = MdcScope.of("jobId", id.toString())) {
      LOGGER.info("job transition phase={} progress={}", phase, clamped);
      jobRepository
          .findById(id)
          .ifPresent(
              snapshot -> {
                if (snapshot.status() == AnalysisJobStatus.QUEUED) {
                  jobRepository.markStarted(id, Instant.now(clock));
                }
                jobRepository.updatePhase(id, phase, clamped, message);
              });
    }
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void updateMetrics(AnalysisJobId id, AnalysisJobMetrics metrics) {
    try (MdcScope ignored = MdcScope.of("jobId", id.toString())) {
      LOGGER.debug("job metrics update {}", metrics);
      jobRepository.updateMetrics(id, metrics);
    }
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void success(AnalysisJobId id, UUID analysisId, AnalysisJobMetrics finalMetrics) {
    try (MdcScope ignored = MdcScope.of("jobId", id.toString())) {
      LOGGER.info("job success analysisId={}", analysisId);
      jobRepository.markSuccess(id, analysisId, finalMetrics, Instant.now(clock));
    }
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fail(AnalysisJobId id, AnalysisJobErrorCode code, String message) {
    try (MdcScope ignored = MdcScope.of("jobId", id.toString())) {
      LOGGER.warn("job failed code={} message={}", code, message);
      jobRepository.markFailed(id, code, message, Instant.now(clock));
    }
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markPricing(AnalysisJobId id, PricingSnapshotHandle handle) {
    if (handle == null) {
      return;
    }
    try (MdcScope ignored = MdcScope.of("jobId", id.toString())) {
      LOGGER.info(
          "job pricing snapshot captured id={} source={}",
          handle.id().value(),
          handle.primarySource());
      jobRepository.updatePricing(id, handle);
    }
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
}
