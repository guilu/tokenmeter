package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Reconciles non-terminal jobs at application start. Jobs left in {@code QUEUED} or {@code RUNNING}
 * by a previous process are transitioned to {@code FAILED/JOB_INTERRUPTED}.
 */
@Component
@Order(0)
public class AnalysisJobReaper implements ApplicationRunner {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisJobReaper.class);

  private final AnalysisJobRepository jobRepository;
  private final Clock clock;

  public AnalysisJobReaper(AnalysisJobRepository jobRepository, Clock clock) {
    this.jobRepository = jobRepository;
    this.clock = clock;
  }

  @Override
  public void run(ApplicationArguments args) {
    reconcile();
  }

  /** Visible for tests — performs the reconciliation pass synchronously. */
  public int reconcile() {
    List<AnalysisJobSnapshot> stale = jobRepository.findNonTerminal();
    if (stale.isEmpty()) {
      return 0;
    }
    Instant now = Instant.now(clock);
    int reconciled = 0;
    for (AnalysisJobSnapshot snapshot : stale) {
      jobRepository.markInterrupted(snapshot.id(), now);
      reconciled++;
    }
    LOGGER.warn(
        "Reconciled {} non-terminal jobs at boot (marked FAILED/JOB_INTERRUPTED)", reconciled);
    return reconciled;
  }
}
