package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisJobRepository;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({
  JpaAnalysisJobRepository.class,
  JpaAnalysisJobProgressEmitter.class,
  JpaAnalysisJobProgressEmitterTest.FixedClockConfig.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Rollback(false)
class JpaAnalysisJobProgressEmitterTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-05-23T09:00:00Z");

  @Autowired private JpaAnalysisJobProgressEmitter emitter;
  @Autowired private AnalysisJobRepository jobRepository;
  @Autowired private AnalysisJobJpaRepository jpaRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @PersistenceContext private EntityManager entityManager;

  @Test
  void transitionClampsProgressTo99() {
    AnalysisJobSnapshot snapshot = newQueued("https://github.com/foo/clamp");
    jobRepository.save(snapshot);

    emitter.transition(snapshot.id(), AnalysisJobPhase.COUNTING_TOKENS, 100, "almost done");

    AnalysisJobEntity entity = jpaRepository.findById(snapshot.id().value()).orElseThrow();
    assertThat(entity.getProgressPercent()).isEqualTo((short) 99);
    assertThat(entity.getPhase()).isEqualTo(AnalysisJobPhase.COUNTING_TOKENS);
    assertThat(entity.getStatus()).isEqualTo(AnalysisJobStatus.RUNNING);
    assertThat(entity.getStartedAt()).isNotNull();
  }

  @Test
  void successPersistsHundredAndCompleted() {
    AnalysisJobSnapshot snapshot = newQueued("https://github.com/foo/success");
    jobRepository.save(snapshot);
    UUID analysisId = UUID.randomUUID();
    insertAnalysisRowFor(analysisId);

    emitter.success(snapshot.id(), analysisId, new AnalysisJobMetrics(10L, 10L, 0L, 100L, 1, 1));

    AnalysisJobEntity entity = jpaRepository.findById(snapshot.id().value()).orElseThrow();
    assertThat(entity.getStatus()).isEqualTo(AnalysisJobStatus.SUCCESS);
    assertThat(entity.getPhase()).isEqualTo(AnalysisJobPhase.COMPLETED);
    assertThat(entity.getProgressPercent()).isEqualTo((short) 100);
    assertThat(entity.getAnalysisId()).isEqualTo(analysisId);
    assertThat(entity.getCompletedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void failIsNoOpOnTerminalJob() {
    AnalysisJobSnapshot snapshot = newQueued("https://github.com/foo/idempotent");
    jobRepository.save(snapshot);
    emitter.fail(snapshot.id(), AnalysisJobErrorCode.CLONE_TIMEOUT, "first");

    emitter.fail(snapshot.id(), AnalysisJobErrorCode.ANALYSIS_FAILED, "second");

    AnalysisJobEntity entity = jpaRepository.findById(snapshot.id().value()).orElseThrow();
    assertThat(entity.getErrorCode()).isEqualTo(AnalysisJobErrorCode.CLONE_TIMEOUT.name());
    assertThat(entity.getErrorMessage()).isEqualTo("first");
  }

  private AnalysisJobSnapshot newQueued(String url) {
    Instant now = FIXED_NOW;
    return new AnalysisJobSnapshot(
        AnalysisJobId.random(),
        url,
        AnalysisJobStatus.QUEUED,
        AnalysisJobPhase.QUEUED,
        0,
        null,
        null,
        null,
        null,
        AnalysisJobMetrics.empty(),
        now,
        null,
        now,
        null);
  }

  private void insertAnalysisRowFor(UUID analysisId) {
    transactionTemplate.executeWithoutResult(
        tx ->
            entityManager
                .createNativeQuery(
                    "INSERT INTO analysis (id, repository_url, clone_url, owner_name, "
                        + "repository_name, status, total_files, total_lines, total_bytes, "
                        + "token_encoding, total_tokens, created_at) VALUES (?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, now())")
                .setParameter(1, analysisId)
                .setParameter(2, "https://github.com/foo/dummy")
                .setParameter(3, "https://github.com/foo/dummy.git")
                .setParameter(4, "foo")
                .setParameter(5, "dummy")
                .setParameter(6, "SUCCESS")
                .setParameter(7, 1L)
                .setParameter(8, 1L)
                .setParameter(9, 1L)
                .setParameter(10, "o200k_base")
                .setParameter(11, 1L)
                .executeUpdate());
  }

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }
  }
}
