package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotId;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Pure unit tests for {@link MarkdownExportRenderer} — no Spring context required. */
class MarkdownExportRendererTest {

  private MarkdownExportRenderer renderer;

  @BeforeEach
  void setUp() {
    renderer = new MarkdownExportRenderer();
  }

  @Test
  void render_containsRepositorySection() {
    RepositoryAnalysisResult analysis = sampleAnalysis(samplePricingHandle());

    String md = renderer.render(analysis);

    assertThat(md).contains("acme").contains("my-repo").contains("https://github.com/acme/my-repo");
  }

  @Test
  void render_containsLanguageBreakdownTable() {
    RepositoryAnalysisResult analysis = sampleAnalysis(samplePricingHandle());

    String md = renderer.render(analysis);

    assertThat(md).contains("Java").contains("1,200");
  }

  @Test
  void render_containsCostEstimatesByMode() {
    RepositoryAnalysisResult analysis = sampleAnalysis(samplePricingHandle());

    String md = renderer.render(analysis);

    assertThat(md).contains("RAW").contains("ASSISTED").contains("AGENTIC");
    assertThat(md).contains("openai").contains("gpt-4o");
  }

  @Test
  void render_containsPricingSnapshotSection() {
    RepositoryAnalysisResult analysis = sampleAnalysis(samplePricingHandle());

    String md = renderer.render(analysis);

    assertThat(md).contains("v1:" + "a".repeat(64)).contains("REMOTE");
  }

  @Test
  void render_withNullPricing_emitsNotAvailableLine() {
    RepositoryAnalysisResult analysis = sampleAnalysis(null);

    String md = renderer.render(analysis);

    assertThat(md).contains("Pricing snapshot: not available");
  }

  @Test
  void render_withNullPricing_doesNotThrow() {
    RepositoryAnalysisResult analysis = sampleAnalysis(null);

    assertThatNoException().isThrownBy(() -> renderer.render(analysis));
  }

  @Test
  void render_withEmptyCostEstimates_emitsNoEstimatesLine() {
    RepositoryAnalysisResult analysis =
        new RepositoryAnalysisResult(
            UUID.randomUUID(),
            Instant.parse("2026-01-15T10:00:00Z"),
            "https://github.com/acme/my-repo",
            "https://github.com/acme/my-repo.git",
            "acme",
            "my-repo",
            sampleScanResult(),
            sampleTokenizationResult(),
            List.of(),
            samplePricingHandle());

    String md = renderer.render(analysis);

    assertThat(md).contains("No cost estimates available");
  }

  @Test
  void render_filename_slugsOwnerAndName() {
    RepositoryAnalysisResult analysis = sampleAnalysis(samplePricingHandle());

    String filename = renderer.filename(analysis);

    assertThat(filename).isEqualTo("tokenmeter-acme-my-repo.md");
  }

  @Test
  void render_filename_fallsBackToIdWhenOwnerBlank() {
    UUID id = UUID.randomUUID();
    RepositoryAnalysisResult analysis =
        new RepositoryAnalysisResult(
            id,
            Instant.parse("2026-01-15T10:00:00Z"),
            "https://github.com/acme/my-repo",
            "https://github.com/acme/my-repo.git",
            "",
            "",
            sampleScanResult(),
            sampleTokenizationResult(),
            List.of(),
            null);

    String filename = renderer.filename(analysis);

    assertThat(filename).isEqualTo("tokenmeter-analysis-" + id + ".md");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static RepositoryAnalysisResult sampleAnalysis(PricingSnapshotHandle pricing) {
    return new RepositoryAnalysisResult(
        UUID.randomUUID(),
        Instant.parse("2026-01-15T10:00:00Z"),
        "https://github.com/acme/my-repo",
        "https://github.com/acme/my-repo.git",
        "acme",
        "my-repo",
        sampleScanResult(),
        sampleTokenizationResult(),
        sampleCostEstimates(),
        pricing);
  }

  private static RepositoryScanResult sampleScanResult() {
    return new RepositoryScanResult(
        3, 150, 8192, List.of(), Map.of("Java", new LanguageStatistics("Java", 3, 150, 8192)));
  }

  private static RepositoryTokenizationResult sampleTokenizationResult() {
    return new RepositoryTokenizationResult(
        "o200k_base",
        3,
        1200,
        Map.of("openai/o200k_base", 1200L),
        List.of(),
        Map.of("Java", new LanguageTokenMetrics("Java", 3, 1200)));
  }

  private static List<ModelCostEstimate> sampleCostEstimates() {
    return List.of(
        new ModelCostEstimate(
            AiProvider.OPENAI,
            "gpt-4o",
            CostEstimationMode.RAW,
            null,
            null,
            1200,
            0,
            1200,
            new BigDecimal("0.000000"),
            new BigDecimal("0.012000"),
            new BigDecimal("0.012000"),
            "1200 output * $10/M"),
        new ModelCostEstimate(
            AiProvider.OPENAI,
            "gpt-4o",
            CostEstimationMode.ASSISTED,
            null,
            null,
            1200,
            1200,
            6000,
            new BigDecimal("0.003000"),
            new BigDecimal("0.060000"),
            new BigDecimal("0.063000"),
            "1200*5 output + 1200 input * $2.5/M"),
        new ModelCostEstimate(
            AiProvider.OPENAI,
            "gpt-4o",
            CostEstimationMode.AGENTIC,
            null,
            null,
            1200,
            4800,
            24000,
            new BigDecimal("0.012000"),
            new BigDecimal("0.240000"),
            new BigDecimal("0.252000"),
            "1200*20 output + 1200*4 input * pricing"));
  }

  private static PricingSnapshotHandle samplePricingHandle() {
    return new PricingSnapshotHandle(
        new PricingSnapshotId("v1:" + "a".repeat(64)),
        PricingSource.REMOTE,
        Instant.parse("2026-05-24T18:42:11Z"),
        List.of());
  }
}
