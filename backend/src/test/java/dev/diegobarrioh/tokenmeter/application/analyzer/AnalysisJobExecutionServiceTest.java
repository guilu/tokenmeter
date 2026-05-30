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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
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
    verify(emitter, atLeastOnce())
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

  @Test
  void countingTokensPercentsAreInRangeAndMonotonic() throws IOException {
    AnalysisJobRepository jobRepository = Mockito.mock(AnalysisJobRepository.class);
    AnalysisJobProgressEmitter emitter = Mockito.mock(AnalysisJobProgressEmitter.class);
    AnalysisJobId jobId = AnalysisJobId.random();
    UUID analysisId = UUID.randomUUID();
    when(jobRepository.findById(eq(jobId)))
        .thenReturn(Optional.of(queuedSnapshot(jobId, "https://github.com/guilu/tokenmeter")));

    AnalysisJobExecutionService service =
        new AnalysisJobExecutionService(
            (repositoryUrl, targetDirectory, timeout) -> {
              // write 19 files so each triggers a 1% step, ensuring all integer steps appear
              for (int i = 0; i < 19; i++) {
                writeFile(
                    targetDirectory,
                    "src/F%02d.java".formatted(i),
                    "public class F%02d {}\n".formatted(i));
              }
            },
            properties(1024 * 1024, Duration.ofSeconds(10)),
            scanner(),
            tokenizationService(),
            persistenceServiceReturning(analysisId),
            costEstimationService(),
            jobRepository,
            emitter,
            identityService());

    service.runJobInternal(jobId);

    ArgumentCaptor<Integer> percentCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<AnalysisJobPhase> phaseCaptor = ArgumentCaptor.forClass(AnalysisJobPhase.class);
    verify(emitter, atLeastOnce())
        .transition(eq(jobId), phaseCaptor.capture(), percentCaptor.capture(), anyString());

    // Filter only COUNTING_TOKENS percents
    List<Integer> countingPercents = new ArrayList<>();
    List<AnalysisJobPhase> phases = phaseCaptor.getAllValues();
    List<Integer> percents = percentCaptor.getAllValues();
    for (int i = 0; i < phases.size(); i++) {
      if (phases.get(i) == AnalysisJobPhase.COUNTING_TOKENS) {
        countingPercents.add(percents.get(i));
      }
    }

    assertThat(countingPercents).isNotEmpty();
    assertThat(countingPercents).allSatisfy(p -> assertThat(p).isBetween(60, 79));
    // monotonically non-decreasing
    for (int i = 1; i < countingPercents.size(); i++) {
      assertThat(countingPercents.get(i)).isGreaterThanOrEqualTo(countingPercents.get(i - 1));
    }
    // first file always emits: at minimum the phase-entry (60) must be present
    assertThat(countingPercents.get(0)).isEqualTo(60);
  }

  @Test
  void intermediateMetricsHaveNullFilesDiscovered() {
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

    ArgumentCaptor<AnalysisJobMetrics> metricsCaptor =
        ArgumentCaptor.forClass(AnalysisJobMetrics.class);
    verify(emitter, atLeastOnce()).updateMetrics(eq(jobId), metricsCaptor.capture());

    // Find intermediate COUNTING_TOKENS emits: filesProcessed != null but filesDiscovered == null
    List<AnalysisJobMetrics> intermediateCountingMetrics =
        metricsCaptor.getAllValues().stream()
            .filter(m -> m.filesProcessed() != null && m.filesDiscovered() == null)
            .toList();

    assertThat(intermediateCountingMetrics).isNotEmpty();
    intermediateCountingMetrics.forEach(
        m -> {
          assertThat(m.filesDiscovered()).isNull();
          assertThat(m.filesProcessed()).isNotNull();
        });
  }

  @Test
  void throttleSuppressesEmitsWhenDeltaPercentIsLessThanOne() {
    // 100 files → each file advances 19*1/100 = 0.19% → below the 1% threshold.
    // Only the first file triggers a forced emit; subsequent files are suppressed
    // until an integer percent boundary is crossed. The total COUNTING_TOKENS
    // transition count must therefore be strictly less than 100 (the file count).
    AnalysisJobRepository jobRepository = Mockito.mock(AnalysisJobRepository.class);
    AnalysisJobProgressEmitter emitter = Mockito.mock(AnalysisJobProgressEmitter.class);
    AnalysisJobId jobId = AnalysisJobId.random();
    UUID analysisId = UUID.randomUUID();
    when(jobRepository.findById(eq(jobId)))
        .thenReturn(Optional.of(queuedSnapshot(jobId, "https://github.com/guilu/tokenmeter")));
    int fileCount = 100;

    AnalysisJobExecutionService service =
        new AnalysisJobExecutionService(
            (repositoryUrl, targetDirectory, timeout) -> {
              for (int i = 0; i < fileCount; i++) {
                writeFile(
                    targetDirectory,
                    "src/F%03d.java".formatted(i),
                    "public class F%03d {}\n".formatted(i));
              }
            },
            properties(1024 * 1024, Duration.ofSeconds(30)),
            scanner(),
            tokenizationService(),
            persistenceServiceReturning(analysisId),
            costEstimationService(),
            jobRepository,
            emitter,
            identityService());

    service.runJobInternal(jobId);

    ArgumentCaptor<AnalysisJobPhase> phaseCaptor = ArgumentCaptor.forClass(AnalysisJobPhase.class);
    verify(emitter, atLeastOnce())
        .transition(eq(jobId), phaseCaptor.capture(), anyInt(), anyString());
    long countingEmits =
        phaseCaptor.getAllValues().stream()
            .filter(p -> p == AnalysisJobPhase.COUNTING_TOKENS)
            .count();

    assertThat(countingEmits).isLessThan(fileCount);
  }

  @Test
  void throttleBoundsEmitCountForLargeRepo() {
    // 1000 files → Δ% per file = 19/1000 ≈ 0.019 → at most 19 distinct integer
    // percent steps (60→79) plus the forced first-file emit. The total
    // COUNTING_TOKENS transition count must not exceed 20.
    AnalysisJobRepository jobRepository = Mockito.mock(AnalysisJobRepository.class);
    AnalysisJobProgressEmitter emitter = Mockito.mock(AnalysisJobProgressEmitter.class);
    AnalysisJobId jobId = AnalysisJobId.random();
    UUID analysisId = UUID.randomUUID();
    when(jobRepository.findById(eq(jobId)))
        .thenReturn(Optional.of(queuedSnapshot(jobId, "https://github.com/guilu/tokenmeter")));
    int fileCount = 1000;

    AnalysisJobExecutionService service =
        new AnalysisJobExecutionService(
            (repositoryUrl, targetDirectory, timeout) -> {
              for (int i = 0; i < fileCount; i++) {
                writeFile(
                    targetDirectory,
                    "src/F%04d.java".formatted(i),
                    "public class F%04d {}\n".formatted(i));
              }
            },
            properties(10L * 1024 * 1024, Duration.ofSeconds(60)),
            scanner(),
            tokenizationService(),
            persistenceServiceReturning(analysisId),
            costEstimationService(),
            jobRepository,
            emitter,
            identityService());

    service.runJobInternal(jobId);

    ArgumentCaptor<AnalysisJobPhase> phaseCaptor = ArgumentCaptor.forClass(AnalysisJobPhase.class);
    verify(emitter, atLeastOnce())
        .transition(eq(jobId), phaseCaptor.capture(), anyInt(), anyString());
    long countingEmits =
        phaseCaptor.getAllValues().stream()
            .filter(p -> p == AnalysisJobPhase.COUNTING_TOKENS)
            .count();

    // Max theoretical bound: phase-entry at 60 (1) + forced first-file at 60 (1)
    // + 19 distinct Δ%≥1 steps [61..79] = 21, plus a small allowance for the
    // time-based fallback branch on slow machines. The key claim is that the
    // throttle keeps the emit count orders-of-magnitude below the file count.
    assertThat(countingEmits).isLessThanOrEqualTo(25);
    assertThat(countingEmits).isLessThan(fileCount);
  }

  @Test
  void intermediateMetricsHaveNullFilesSkipped() {
    // Intermediate COUNTING_TOKENS updateMetrics emits must carry null for both
    // filesDiscovered AND filesSkipped — only the final emit after tokenization
    // carries the real totals.
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

    ArgumentCaptor<AnalysisJobMetrics> metricsCaptor =
        ArgumentCaptor.forClass(AnalysisJobMetrics.class);
    verify(emitter, atLeastOnce()).updateMetrics(eq(jobId), metricsCaptor.capture());

    List<AnalysisJobMetrics> intermediateCountingMetrics =
        metricsCaptor.getAllValues().stream()
            .filter(m -> m.filesProcessed() != null && m.filesDiscovered() == null)
            .toList();

    assertThat(intermediateCountingMetrics).isNotEmpty();
    intermediateCountingMetrics.forEach(
        m -> {
          assertThat(m.filesDiscovered()).isNull();
          assertThat(m.filesSkipped()).isNull();
        });
  }

  @Test
  void singleFileRepoEmitsCountingTokensAtPercent79() {
    // single file: percent = min(79, 60 + (1 * 19 / 1)) = 60 + 19 = 79
    AnalysisJobRepository jobRepository = Mockito.mock(AnalysisJobRepository.class);
    AnalysisJobProgressEmitter emitter = Mockito.mock(AnalysisJobProgressEmitter.class);
    AnalysisJobId jobId = AnalysisJobId.random();
    UUID analysisId = UUID.randomUUID();
    when(jobRepository.findById(eq(jobId)))
        .thenReturn(Optional.of(queuedSnapshot(jobId, "https://github.com/guilu/tokenmeter")));

    AnalysisJobExecutionService service =
        new AnalysisJobExecutionService(
            (repositoryUrl, targetDirectory, timeout) ->
                writeFile(targetDirectory, "src/Only.java", "public class Only {}\n"),
            properties(1024, Duration.ofSeconds(2)),
            scanner(),
            tokenizationService(),
            persistenceServiceReturning(analysisId),
            costEstimationService(),
            jobRepository,
            emitter,
            identityService());

    service.runJobInternal(jobId);

    ArgumentCaptor<Integer> percentCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<AnalysisJobPhase> phaseCaptor = ArgumentCaptor.forClass(AnalysisJobPhase.class);
    verify(emitter, atLeastOnce())
        .transition(eq(jobId), phaseCaptor.capture(), percentCaptor.capture(), anyString());

    List<Integer> countingPercents = new ArrayList<>();
    List<AnalysisJobPhase> phases = phaseCaptor.getAllValues();
    List<Integer> percents = percentCaptor.getAllValues();
    for (int i = 0; i < phases.size(); i++) {
      if (phases.get(i) == AnalysisJobPhase.COUNTING_TOKENS) {
        countingPercents.add(percents.get(i));
      }
    }

    // The listener fires once for the only file; processed=1, total=1 → percent=79.
    // The phase-entry emit (percent=60) comes from the pre-loop transition call;
    // the listener emit comes right after for processed==1 → percent=79.
    assertThat(countingPercents).contains(79);
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
