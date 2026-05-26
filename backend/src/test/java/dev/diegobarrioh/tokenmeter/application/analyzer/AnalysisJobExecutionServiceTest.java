package dev.diegobarrioh.tokenmeter.application.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.application.cost.RepositoryCostEstimationService;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingSnapshotIdentityService;
import dev.diegobarrioh.tokenmeter.application.repository.RepositoryIntakeProperties;
import dev.diegobarrioh.tokenmeter.application.tokenizer.OpenAiTokenCounter;
import dev.diegobarrioh.tokenmeter.application.tokenizer.RepositoryTokenizationService;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class AnalysisJobExecutionServiceTest {
  @TempDir Path tempDir;

  @Test
  void runsHappyPathAndEmitsSuccess() {
    AnalysisJobRepository jobRepository = Mockito.mock(AnalysisJobRepository.class);
    AnalysisJobProgressEmitter emitter = Mockito.mock(AnalysisJobProgressEmitter.class);
    AnalysisJobId jobId = AnalysisJobId.random();
    UUID analysisId = UUID.randomUUID();
    when(jobRepository.findById(eq(jobId)))
        .thenReturn(Optional.of(queuedSnapshot(jobId, "https://github.com/guilu/tokenmeter")));

    AnalysisJobExecutionService service =
        new AnalysisJobExecutionService(
            (repositoryUrl, targetDirectory, timeout) ->
                writeFile(targetDirectory, "src/App.java", "class App {}\n"),
            properties(1024, Duration.ofSeconds(2)),
            scanner(),
            tokenizationService(),
            persistenceServiceReturning(analysisId),
            costEstimationService(),
            jobRepository,
            emitter,
            identityService());

    service.runJobInternal(jobId);

    verify(emitter)
        .transition(eq(jobId), eq(AnalysisJobPhase.CHECKING_CACHE), anyInt(), anyString());
    verify(emitter)
        .transition(eq(jobId), eq(AnalysisJobPhase.CLONING_REPOSITORY), anyInt(), anyString());
    verify(emitter)
        .transition(eq(jobId), eq(AnalysisJobPhase.SCANNING_FILES), anyInt(), anyString());
    verify(emitter)
        .transition(eq(jobId), eq(AnalysisJobPhase.FILTERING_FILES), anyInt(), anyString());
    verify(emitter)
        .transition(eq(jobId), eq(AnalysisJobPhase.COUNTING_TOKENS), anyInt(), anyString());
    verify(emitter)
        .transition(eq(jobId), eq(AnalysisJobPhase.CALCULATING_COSTS), anyInt(), anyString());
    verify(emitter)
        .transition(eq(jobId), eq(AnalysisJobPhase.SAVING_REPORT), anyInt(), anyString());
    verify(emitter, atLeastOnce()).updateMetrics(eq(jobId), any(AnalysisJobMetrics.class));
    verify(emitter).success(eq(jobId), eq(analysisId), any(AnalysisJobMetrics.class));
    verify(emitter, never()).fail(any(), any(), any());
    assertThat(tempDir).isEmptyDirectory();
  }

  @Test
  void emitsFailWithCloneTimeoutWhenClonerThrows() {
    AnalysisJobRepository jobRepository = Mockito.mock(AnalysisJobRepository.class);
    AnalysisJobProgressEmitter emitter = Mockito.mock(AnalysisJobProgressEmitter.class);
    AnalysisJobId jobId = AnalysisJobId.random();
    when(jobRepository.findById(eq(jobId)))
        .thenReturn(Optional.of(queuedSnapshot(jobId, "https://github.com/guilu/slow")));

    AnalysisJobExecutionService service =
        new AnalysisJobExecutionService(
            (repositoryUrl, targetDirectory, timeout) -> {
              throw new RepositoryIntakeException(
                  RepositoryIntakeErrorCode.CLONE_TIMEOUT,
                  "Repository clone exceeded timeout of " + timeout.toSeconds() + " seconds");
            },
            properties(1024, Duration.ofMillis(50)),
            scanner(),
            tokenizationService(),
            persistenceServiceReturning(UUID.randomUUID()),
            costEstimationService(),
            jobRepository,
            emitter,
            identityService());

    service.runJobInternal(jobId);

    verify(emitter).fail(eq(jobId), eq(AnalysisJobErrorCode.CLONE_TIMEOUT), anyString());
    verify(emitter, never()).success(any(), any(), any());
    assertThat(tempDir).isEmptyDirectory();
  }

  @Test
  void mapsRepositoryTooLargeToFailureCode() {
    AnalysisJobRepository jobRepository = Mockito.mock(AnalysisJobRepository.class);
    AnalysisJobProgressEmitter emitter = Mockito.mock(AnalysisJobProgressEmitter.class);
    AnalysisJobId jobId = AnalysisJobId.random();
    when(jobRepository.findById(eq(jobId)))
        .thenReturn(Optional.of(queuedSnapshot(jobId, "https://github.com/guilu/huge")));

    AnalysisJobExecutionService service =
        new AnalysisJobExecutionService(
            (repositoryUrl, targetDirectory, timeout) ->
                writeFile(targetDirectory, "large.txt", "too large"),
            properties(3, Duration.ofSeconds(2)),
            scanner(),
            tokenizationService(),
            persistenceServiceReturning(UUID.randomUUID()),
            costEstimationService(),
            jobRepository,
            emitter,
            identityService());

    service.runJobInternal(jobId);

    verify(emitter).fail(eq(jobId), eq(AnalysisJobErrorCode.REPOSITORY_TOO_LARGE), anyString());
    verify(emitter, never()).success(any(), any(), any());
    assertThat(tempDir).isEmptyDirectory();
  }

  @Test
  void unexpectedExceptionFallsBackToAnalysisFailed() {
    AnalysisJobRepository jobRepository = Mockito.mock(AnalysisJobRepository.class);
    AnalysisJobProgressEmitter emitter = Mockito.mock(AnalysisJobProgressEmitter.class);
    AnalysisJobId jobId = AnalysisJobId.random();
    when(jobRepository.findById(eq(jobId)))
        .thenReturn(Optional.of(queuedSnapshot(jobId, "https://github.com/guilu/tokenmeter")));

    AnalysisJobExecutionService service =
        new AnalysisJobExecutionService(
            (repositoryUrl, targetDirectory, timeout) -> {
              throw new IllegalStateException("boom");
            },
            properties(1024, Duration.ofSeconds(2)),
            scanner(),
            tokenizationService(),
            persistenceServiceReturning(UUID.randomUUID()),
            costEstimationService(),
            jobRepository,
            emitter,
            identityService());

    service.runJobInternal(jobId);

    verify(emitter).fail(eq(jobId), eq(AnalysisJobErrorCode.ANALYSIS_FAILED), anyString());
  }

  @Test
  void doesNothingIfJobIsAlreadyTerminal() {
    AnalysisJobRepository jobRepository = Mockito.mock(AnalysisJobRepository.class);
    AnalysisJobProgressEmitter emitter = Mockito.mock(AnalysisJobProgressEmitter.class);
    AnalysisJobId jobId = AnalysisJobId.random();
    AnalysisJobSnapshot terminal =
        new AnalysisJobSnapshot(
            jobId,
            "https://github.com/guilu/tokenmeter",
            AnalysisJobStatus.SUCCESS,
            AnalysisJobPhase.COMPLETED,
            100,
            null,
            UUID.randomUUID(),
            null,
            null,
            AnalysisJobMetrics.empty(),
            Instant.now(),
            null,
            Instant.now(),
            Instant.now());
    when(jobRepository.findById(eq(jobId))).thenReturn(Optional.of(terminal));

    AnalysisJobExecutionService service =
        new AnalysisJobExecutionService(
            (repositoryUrl, targetDirectory, timeout) -> {
              throw new IllegalStateException("must not be called");
            },
            properties(1024, Duration.ofSeconds(2)),
            scanner(),
            tokenizationService(),
            persistenceServiceReturning(UUID.randomUUID()),
            costEstimationService(),
            jobRepository,
            emitter,
            identityService());

    service.runJobInternal(jobId);

    verify(emitter, never()).transition(any(), any(), anyInt(), anyString());
    verify(emitter, never()).success(any(), any(), any());
    verify(emitter, never()).fail(any(), any(), any());
  }

  private RepositoryIntakeProperties properties(long maxBytes, Duration timeout) {
    return new RepositoryIntakeProperties(tempDir, maxBytes, timeout);
  }

  private AnalysisJobSnapshot queuedSnapshot(AnalysisJobId id, String repositoryUrl) {
    Instant now = Instant.now();
    return new AnalysisJobSnapshot(
        id,
        repositoryUrl,
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

  private static RepositoryFileScanner scanner() {
    return new RepositoryFileScanner(new FileLanguageDetector(), new BinaryFileDetector());
  }

  private static RepositoryTokenizationService tokenizationService() {
    return new RepositoryTokenizationService(new OpenAiTokenCounter());
  }

  private static AnalysisPersistenceService persistenceServiceReturning(UUID assignedId) {
    return new AnalysisPersistenceService() {
      @Override
      public RepositoryAnalysisResult save(RepositoryAnalysisResult result) {
        return new RepositoryAnalysisResult(
            assignedId,
            Instant.now(),
            result.repositoryUrl(),
            result.cloneUrl(),
            result.owner(),
            result.name(),
            result.scan(),
            result.tokenization(),
            result.costEstimates());
      }

      @Override
      public Optional<RepositoryAnalysisResult> findById(UUID id) {
        return Optional.empty();
      }

      @Override
      public Optional<UUID> findLatestSuccessIdFor(String repositoryUrl, String pricingSnapshotId) {
        return Optional.empty();
      }
    };
  }

  private static RepositoryCostEstimationService costEstimationService() {
    return new RepositoryCostEstimationService(testPricingProvider());
  }

  private static PricingSnapshotIdentityService identityService() {
    return new PricingSnapshotIdentityService(testPricingProvider(), Clock.systemUTC());
  }

  private static PricingProvider testPricingProvider() {
    return new PricingProvider() {
      @Override
      public List<ModelPricing> all() {
        return List.of(
            new ModelPricing(
                AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")));
      }

      @Override
      public Optional<ModelPricing> find(AiProvider provider, String model) {
        return Optional.empty();
      }
    };
  }

  private static void writeFile(Path directory, String relativePath, String content) {
    try {
      Path file = directory.resolve(relativePath);
      Files.createDirectories(file.getParent());
      Files.writeString(file, content);
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
