package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AnalysisConcurrencyGuardTest {

  @Test
  void allowsUpToMaxConcurrentAcquires() {
    AnalysisConcurrencyGuard guard = guardWithMax(2);

    guard.acquire();
    guard.acquire();

    assertThatThrownBy(guard::acquire)
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.RATE_LIMITED);
  }

  @Test
  void releasedPermitAllowsNewAcquire() {
    AnalysisConcurrencyGuard guard = guardWithMax(1);

    guard.acquire();
    guard.release();

    assertThatNoException().isThrownBy(guard::acquire);
  }

  @Test
  void singlePermitGuardRejectsSecondConcurrentAcquire() {
    AnalysisConcurrencyGuard guard = guardWithMax(1);
    guard.acquire();

    assertThatThrownBy(guard::acquire)
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.RATE_LIMITED);
  }

  private static AnalysisConcurrencyGuard guardWithMax(int max) {
    return new AnalysisConcurrencyGuard(
        new AnalyzeThrottleProperties(max, 5, Duration.ofMinutes(1)));
  }
}
