package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(JpaAnalysisJobRepository.class)
class JpaAnalysisJobRepositoryTest {

  @Autowired private JpaAnalysisJobRepository repository;
  @Autowired private AnalysisJobJpaRepository jpaRepository;
  @Autowired private TransactionTemplate transactionTemplate;
  @PersistenceContext private EntityManager entityManager;

  @Test
  void persistsInitialQueuedJob() {
    AnalysisJobSnapshot snapshot = newQueued("https://github.com/foo/bar");

    AnalysisJobSnapshot persisted = repository.save(snapshot);

    assertThat(persisted.id()).isEqualTo(snapshot.id());
    assertThat(persisted.status()).isEqualTo(AnalysisJobStatus.QUEUED);
    assertThat(jpaRepository.findById(snapshot.id().value())).isPresent();
  }

  @Test
  void updatePhaseClampsProgressTo99WhileRunning() {
    AnalysisJobSnapshot snapshot = newQueued("https://github.com/foo/clamp");
    repository.save(snapshot);

    transactionTemplate.executeWithoutResult(
        status ->
            repository.updatePhase(
                snapshot.id(), AnalysisJobPhase.COUNTING_TOKENS, 200, "tokenizing"));

    AnalysisJobEntity entity = jpaRepository.findById(snapshot.id().value()).orElseThrow();
    assertThat(entity.getProgressPercent()).isLessThanOrEqualTo((short) 99);
    assertThat(entity.getPhase()).isEqualTo(AnalysisJobPhase.COUNTING_TOKENS);
  }

  @Test
  void markSuccessSetsHundredAndCompleted() {
    AnalysisJobSnapshot snapshot = newQueued("https://github.com/foo/success");
    repository.save(snapshot);
    Instant now = Instant.now();
    UUID analysisId = UUID.randomUUID();
    insertAnalysisRowFor(analysisId);

    repository.markSuccess(
        snapshot.id(), analysisId, new AnalysisJobMetrics(10L, 10L, 0L, 100L, 1, 1), now);

    AnalysisJobEntity entity = jpaRepository.findById(snapshot.id().value()).orElseThrow();
    assertThat(entity.getStatus()).isEqualTo(AnalysisJobStatus.SUCCESS);
    assertThat(entity.getPhase()).isEqualTo(AnalysisJobPhase.COMPLETED);
    assertThat(entity.getProgressPercent()).isEqualTo((short) 100);
    assertThat(entity.getAnalysisId()).isEqualTo(analysisId);
    assertThat(entity.getCompletedAt()).isNotNull();
  }

  @Test
  void databaseRejectsProgress100WithoutAnalysisId() {
    AnalysisJobSnapshot snapshot = newQueued("https://github.com/foo/invariant");
    repository.save(snapshot);

    assertThatThrownBy(
            () ->
                transactionTemplate.executeWithoutResult(
                    status -> {
                      AnalysisJobEntity entity =
                          jpaRepository.findById(snapshot.id().value()).orElseThrow();
                      entity.setProgressPercent((short) 100);
                      jpaRepository.flush();
                    }))
        .isNotNull();
  }

  @Test
  void findNonTerminalReturnsQueuedAndRunningJobs() {
    AnalysisJobSnapshot queued = newQueued("https://github.com/foo/q");
    AnalysisJobSnapshot running = newQueued("https://github.com/foo/r");
    AnalysisJobSnapshot terminal = newQueued("https://github.com/foo/t");

    repository.save(queued);
    repository.save(running);
    repository.save(terminal);
    repository.markStarted(running.id(), Instant.now());
    repository.markFailed(
        terminal.id(), AnalysisJobErrorCode.ANALYSIS_FAILED, "boom", Instant.now());

    assertThat(repository.findNonTerminal())
        .extracting(s -> s.id().value())
        .containsExactlyInAnyOrder(queued.id().value(), running.id().value());
  }

  @Test
  void deleteCompletedBeforeRemovesOnlyOldTerminalRows() {
    AnalysisJobSnapshot old = newQueued("https://github.com/foo/old");
    AnalysisJobSnapshot fresh = newQueued("https://github.com/foo/fresh");
    repository.save(old);
    repository.save(fresh);

    Instant tenDaysAgo = Instant.now().minus(10, ChronoUnit.DAYS);
    repository.markFailed(old.id(), AnalysisJobErrorCode.ANALYSIS_FAILED, "boom", tenDaysAgo);
    repository.markFailed(
        fresh.id(), AnalysisJobErrorCode.ANALYSIS_FAILED, "boom", Instant.now());

    int removed =
        repository.deleteCompletedBefore(
            AnalysisJobStatus.FAILED, Instant.now().minus(1, ChronoUnit.DAYS));

    assertThat(removed).isEqualTo(1);
    assertThat(jpaRepository.findById(old.id().value())).isEmpty();
    assertThat(jpaRepository.findById(fresh.id().value())).isPresent();
  }

  @Test
  void markFailedIsIdempotentOnTerminalJobs() {
    AnalysisJobSnapshot snapshot = newQueued("https://github.com/foo/idempotent");
    repository.save(snapshot);
    Instant first = Instant.now();
    repository.markFailed(snapshot.id(), AnalysisJobErrorCode.CLONE_TIMEOUT, "timeout", first);

    Instant later = first.plusSeconds(10);
    repository.markFailed(
        snapshot.id(), AnalysisJobErrorCode.ANALYSIS_FAILED, "different", later);

    AnalysisJobEntity entity = jpaRepository.findById(snapshot.id().value()).orElseThrow();
    assertThat(entity.getErrorCode()).isEqualTo(AnalysisJobErrorCode.CLONE_TIMEOUT.name());
    assertThat(entity.getErrorMessage()).isEqualTo("timeout");
  }

  private AnalysisJobSnapshot newQueued(String url) {
    Instant now = Instant.now();
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
}
