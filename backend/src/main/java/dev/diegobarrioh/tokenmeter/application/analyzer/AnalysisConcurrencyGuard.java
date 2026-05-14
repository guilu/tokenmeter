package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;

@Component
public class AnalysisConcurrencyGuard {

  private final Semaphore semaphore;

  public AnalysisConcurrencyGuard(AnalyzeThrottleProperties properties) {
    this.semaphore = new Semaphore(properties.maxConcurrent(), true);
  }

  public void acquire() {
    if (!semaphore.tryAcquire()) {
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.RATE_LIMITED,
          "Server is busy: too many concurrent analyses. Please retry shortly.");
    }
  }

  public void release() {
    semaphore.release();
  }

  int availablePermits() {
    return semaphore.availablePermits();
  }
}
