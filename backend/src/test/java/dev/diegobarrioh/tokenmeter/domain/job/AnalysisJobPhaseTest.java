package dev.diegobarrioh.tokenmeter.domain.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class AnalysisJobPhaseTest {

  @Test
  void happyPathIsForwardOnly() {
    AnalysisJobPhase[] happyPath = {
      AnalysisJobPhase.QUEUED,
      AnalysisJobPhase.CHECKING_CACHE,
      AnalysisJobPhase.CLONING_REPOSITORY,
      AnalysisJobPhase.SCANNING_FILES,
      AnalysisJobPhase.FILTERING_FILES,
      AnalysisJobPhase.COUNTING_TOKENS,
      AnalysisJobPhase.CALCULATING_COSTS,
      AnalysisJobPhase.SAVING_REPORT,
      AnalysisJobPhase.COMPLETED,
    };
    for (int i = 0; i < happyPath.length - 1; i++) {
      AnalysisJobPhase current = happyPath[i];
      AnalysisJobPhase next = happyPath[i + 1];
      assertThat(current.canTransitionTo(next))
          .as("happy-path transition %s -> %s must be legal", current, next)
          .isTrue();
    }
  }

  @Test
  void canSkipIntermediatePhasesForward() {
    assertThat(AnalysisJobPhase.QUEUED.canTransitionTo(AnalysisJobPhase.CLONING_REPOSITORY))
        .isTrue();
    assertThat(AnalysisJobPhase.SCANNING_FILES.canTransitionTo(AnalysisJobPhase.COUNTING_TOKENS))
        .isTrue();
  }

  @Test
  void cannotRegressBackwards() {
    assertThat(AnalysisJobPhase.COUNTING_TOKENS.canTransitionTo(AnalysisJobPhase.SCANNING_FILES))
        .isFalse();
    assertThat(AnalysisJobPhase.CLONING_REPOSITORY.canTransitionTo(AnalysisJobPhase.QUEUED))
        .isFalse();
  }

  @Test
  void anyNonTerminalCanJumpToFailed() {
    EnumSet<AnalysisJobPhase> nonTerminals =
        EnumSet.complementOf(EnumSet.of(AnalysisJobPhase.COMPLETED, AnalysisJobPhase.FAILED));
    for (AnalysisJobPhase phase : nonTerminals) {
      assertThat(phase.canTransitionTo(AnalysisJobPhase.FAILED))
          .as("non-terminal %s must be allowed to jump to FAILED", phase)
          .isTrue();
    }
  }

  @Test
  void completedIsTerminalAndImmutable() {
    assertThat(AnalysisJobPhase.COMPLETED.isTerminal()).isTrue();
    for (AnalysisJobPhase next : AnalysisJobPhase.values()) {
      assertThat(AnalysisJobPhase.COMPLETED.canTransitionTo(next))
          .as("COMPLETED must not transition to %s", next)
          .isFalse();
    }
  }

  @Test
  void failedIsTerminalAndImmutable() {
    assertThat(AnalysisJobPhase.FAILED.isTerminal()).isTrue();
    for (AnalysisJobPhase next : AnalysisJobPhase.values()) {
      assertThat(AnalysisJobPhase.FAILED.canTransitionTo(next))
          .as("FAILED must not transition to %s", next)
          .isFalse();
    }
  }

  @Test
  void selfTransitionsAreRejected() {
    for (AnalysisJobPhase phase : AnalysisJobPhase.values()) {
      assertThat(phase.canTransitionTo(phase))
          .as("self-transition %s -> %s must be rejected", phase, phase)
          .isFalse();
    }
  }

  @Test
  void nullTargetIsRejected() {
    assertThat(AnalysisJobPhase.QUEUED.canTransitionTo(null)).isFalse();
  }
}
