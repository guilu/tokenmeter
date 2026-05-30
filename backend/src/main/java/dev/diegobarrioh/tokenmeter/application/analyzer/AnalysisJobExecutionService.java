package dev.diegobarrioh.tokenmeter.application.analyzer;

import dev.diegobarrioh.tokenmeter.application.cost.RepositoryCostEstimationService;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingSnapshotIdentityService;
import dev.diegobarrioh.tokenmeter.application.repository.GitRepositoryCloner;
import dev.diegobarrioh.tokenmeter.application.repository.RepositoryIntakeProperties;
import dev.diegobarrioh.tokenmeter.application.repository.RepositorySizeCalculator;
import dev.diegobarrioh.tokenmeter.application.tokenizer.RepositoryTokenizationService;
import dev.diegobarrioh.tokenmeter.application.tokenizer.TokenizationProgressListener;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobErrorCode;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobId;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobMetrics;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobSnapshot;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.repository.GitHubRepositoryUrl;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryCloneSummary;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs the cloning/tokenization/cost-estimation pipeline asynchronously and emits progress
 * transitions to the {@link AnalysisJobProgressEmitter}. Designed to be invoked through the {@code
 * analysisJobExecutor} bean wired in batch 2; can also be invoked synchronously by tests via {@link
 * #runJobInternal(AnalysisJobId)}.
 */
@Service
public class AnalysisJobExecutionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnalysisJobExecutionService.class);

  private final GitRepositoryCloner cloner;
  private final RepositoryIntakeProperties properties;
  private final RepositoryFileScanner fileScanner;
  private final RepositoryTokenizationService tokenizationService;
  private final AnalysisPersistenceService persistenceService;
  private final RepositoryCostEstimationService costEstimationService;
  private final RepositorySizeCalculator sizeCalculator;
  private final AnalysisJobRepository jobRepository;
  private final AnalysisJobProgressEmitter emitter;
  private final PricingSnapshotIdentityService pricingIdentityService;

  @Autowired
  public AnalysisJobExecutionService(
      GitRepositoryCloner cloner,
      RepositoryIntakeProperties properties,
      RepositoryFileScanner fileScanner,
      RepositoryTokenizationService tokenizationService,
      AnalysisPersistenceService persistenceService,
      RepositoryCostEstimationService costEstimationService,
      AnalysisJobRepository jobRepository,
      AnalysisJobProgressEmitter emitter,
      PricingSnapshotIdentityService pricingIdentityService) {
    this(
        cloner,
        properties,
        fileScanner,
        tokenizationService,
        persistenceService,
        costEstimationService,
        new RepositorySizeCalculator(),
        jobRepository,
        emitter,
        pricingIdentityService);
  }

  AnalysisJobExecutionService(
      GitRepositoryCloner cloner,
      RepositoryIntakeProperties properties,
      RepositoryFileScanner fileScanner,
      RepositoryTokenizationService tokenizationService,
      AnalysisPersistenceService persistenceService,
      RepositoryCostEstimationService costEstimationService,
      RepositorySizeCalculator sizeCalculator,
      AnalysisJobRepository jobRepository,
      AnalysisJobProgressEmitter emitter,
      PricingSnapshotIdentityService pricingIdentityService) {
    this.cloner = cloner;
    this.properties = properties;
    this.fileScanner = fileScanner;
    this.tokenizationService = tokenizationService;
    this.persistenceService = persistenceService;
    this.costEstimationService = costEstimationService;
    this.sizeCalculator = sizeCalculator;
    this.jobRepository = jobRepository;
    this.emitter = emitter;
    this.pricingIdentityService = pricingIdentityService;
  }

  /** Async entry point: executes on the {@code analysisJobExecutor} thread pool. */
  @Async("analysisJobExecutor")
  public void runJob(AnalysisJobId id) {
    runJobInternal(id);
  }

  /**
   * Synchronous core of the pipeline. Visible for tests; production callers should prefer {@link
   * #runJob(AnalysisJobId)}.
   */
  public void runJobInternal(AnalysisJobId id) {
    AnalysisJobSnapshot snapshot = jobRepository.findById(id).orElse(null);
    if (snapshot == null) {
      LOGGER.warn("runJobInternal called for unknown jobId={}", id);
      return;
    }
    if (snapshot.status().isTerminal()) {
      LOGGER.warn(
          "runJobInternal called for already terminal jobId={} status={}", id, snapshot.status());
      return;
    }

    GitHubRepositoryUrl repositoryUrl;
    try {
      repositoryUrl = GitHubRepositoryUrl.parse(snapshot.repositoryUrl());
    } catch (RepositoryIntakeException ex) {
      emitter.fail(id, AnalysisJobErrorCode.fromIntakeCode(ex.errorCode()), ex.getMessage());
      return;
    }

    Path cloneDirectory = null;
    try {
      emitter.transition(id, AnalysisJobPhase.CHECKING_CACHE, 5, "Preparing workspace");

      cloneDirectory = createCloneDirectory(repositoryUrl);
      emitter.transition(id, AnalysisJobPhase.CLONING_REPOSITORY, 15, "Cloning repository");
      cloner.clone(repositoryUrl, cloneDirectory, properties.cloneTimeout());

      emitter.transition(id, AnalysisJobPhase.SCANNING_FILES, 35, "Scanning files");
      RepositoryCloneSummary summary = sizeCalculator.summarize(cloneDirectory);
      enforceSizeLimit(summary);
      RepositoryScanResult scan = fileScanner.scan(cloneDirectory);
      emitter.updateMetrics(
          id, new AnalysisJobMetrics((long) scan.totalFiles(), null, null, null, null, null));

      emitter.transition(id, AnalysisJobPhase.FILTERING_FILES, 45, "Filtering binary files");

      emitter.transition(id, AnalysisJobPhase.COUNTING_TOKENS, 60, "Counting tokens");
      final int[] lastEmittedPercent = {60};
      final long[] lastEmitNanos = {System.nanoTime()};
      TokenizationProgressListener listener =
          (processed, total, tokensSoFar) -> {
            int percent = (total <= 0) ? 60 : Math.min(79, 60 + (int) (processed * 19L / total));
            long now = System.nanoTime();
            boolean first = processed == 1;
            boolean deltaPct = percent - lastEmittedPercent[0] >= 1;
            boolean elapsed = now - lastEmitNanos[0] >= 1_000_000_000L;
            if (first || deltaPct || elapsed) {
              emitter.transition(id, AnalysisJobPhase.COUNTING_TOKENS, percent, "Counting tokens");
              emitter.updateMetrics(
                  id, new AnalysisJobMetrics(null, processed, null, tokensSoFar, null, null));
              lastEmittedPercent[0] = percent;
              lastEmitNanos[0] = now;
            }
          };
      RepositoryTokenizationResult tokenization =
          tokenizationService.tokenize(cloneDirectory, scan, listener);
      emitter.updateMetrics(
          id,
          new AnalysisJobMetrics(
              (long) scan.totalFiles(),
              (long) tokenization.totalFiles(),
              null,
              tokenization.totalTokens(),
              null,
              null));

      emitter.transition(id, AnalysisJobPhase.CALCULATING_COSTS, 80, "Estimating costs");
      PricingSnapshotHandle pricingHandle = pricingIdentityService.capture();
      emitter.markPricing(id, pricingHandle);
      List<ModelCostEstimate> costEstimates =
          costEstimationService.estimate(tokenization.totalTokens(), pricingHandle);
      emitter.updateMetrics(
          id,
          new AnalysisJobMetrics(
              (long) scan.totalFiles(),
              (long) tokenization.totalFiles(),
              null,
              tokenization.totalTokens(),
              null,
              costEstimates.size()));

      emitter.transition(id, AnalysisJobPhase.SAVING_REPORT, 95, "Saving report");
      RepositoryAnalysisResult persisted =
          persistenceService.save(
              new RepositoryAnalysisResult(
                  repositoryUrl.normalizedUrl(),
                  repositoryUrl.cloneUrl(),
                  repositoryUrl.owner(),
                  repositoryUrl.name(),
                  scan,
                  tokenization,
                  costEstimates,
                  pricingHandle));

      AnalysisJobMetrics finalMetrics =
          new AnalysisJobMetrics(
              (long) scan.totalFiles(),
              (long) tokenization.totalFiles(),
              null,
              tokenization.totalTokens(),
              null,
              costEstimates.size());
      emitter.success(id, persisted.id(), finalMetrics);
    } catch (RepositoryIntakeException ex) {
      emitter.fail(id, AnalysisJobErrorCode.fromIntakeCode(ex.errorCode()), ex.getMessage());
    } catch (Throwable t) {
      LOGGER.error("Unexpected failure running analysis job {}", id, t);
      String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
      emitter.fail(id, AnalysisJobErrorCode.ANALYSIS_FAILED, message);
      if (t instanceof Error error) {
        throw error;
      }
    } finally {
      if (cloneDirectory != null && !deleteRecursively(cloneDirectory)) {
        LOGGER.warn("Could not fully clean temporary analysis directory {}", cloneDirectory);
      }
    }
  }

  /** Convenience for tests that want to assert the status of a job after running. */
  AnalysisJobStatus statusOf(AnalysisJobId id) {
    return jobRepository.findById(id).map(AnalysisJobSnapshot::status).orElse(null);
  }

  private Path createCloneDirectory(GitHubRepositoryUrl repositoryUrl) {
    try {
      Files.createDirectories(properties.tempDirectory());
      return Files.createTempDirectory(
          properties.tempDirectory(), repositoryUrl.owner() + "-" + repositoryUrl.name() + "-");
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not create temporary clone directory", exception);
    }
  }

  private void enforceSizeLimit(RepositoryCloneSummary summary) {
    if (summary.totalBytes() > properties.maxRepositoryBytes()) {
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.REPOSITORY_TOO_LARGE,
          "Repository size "
              + summary.totalBytes()
              + " bytes exceeds limit of "
              + properties.maxRepositoryBytes()
              + " bytes");
    }
  }

  private boolean deleteRecursively(Path path) {
    if (!Files.exists(path)) {
      return true;
    }
    try (var paths = Files.walk(path)) {
      paths.sorted(Comparator.reverseOrder()).forEach(AnalysisJobExecutionService::deleteIfExists);
      return !Files.exists(path);
    } catch (IOException exception) {
      LOGGER.warn("Could not delete temporary path {}", path, exception);
      return false;
    }
  }

  private static void deleteIfExists(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException exception) {
      throw new UncheckedIOException("Could not delete temporary path " + path, exception);
    }
  }
}
