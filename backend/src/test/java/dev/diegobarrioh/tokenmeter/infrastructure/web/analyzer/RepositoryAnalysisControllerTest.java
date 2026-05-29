package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisNotFoundException;
import dev.diegobarrioh.tokenmeter.application.analyzer.AnalyzeThrottleProperties;
import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisService;
import dev.diegobarrioh.tokenmeter.application.cost.EngineeringEffortEstimator;
import dev.diegobarrioh.tokenmeter.application.cost.EngineeringEffortProperties;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotId;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import dev.diegobarrioh.tokenmeter.infrastructure.web.PublicOriginProperties;
import dev.diegobarrioh.tokenmeter.infrastructure.web.WebMvcConfiguration;
import dev.diegobarrioh.tokenmeter.infrastructure.web.repository.RepositoryIntakeExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the GET endpoints owned by {@link RepositoryAnalysisController} (cost breakdown, badge,
 * OG image, public HTML, leaderboards). The {@code POST /api/analyze} endpoint has been moved to
 * {@link AnalysisJobController} and is covered by {@code AnalysisJobControllerTest}.
 */
@WebMvcTest(RepositoryAnalysisController.class)
@Import({
  RepositoryAnalysisMapper.class,
  CostBreakdownMapper.class,
  EngineeringEffortEstimator.class,
  OpenGraphImageRenderer.class,
  BadgeRenderer.class,
  MarkdownExportRenderer.class,
  RepositoryIntakeExceptionHandler.class,
  AnalyzeRateLimitInterceptor.class,
  WebMvcConfiguration.class,
})
@EnableConfigurationProperties({
  EngineeringEffortProperties.class,
  AnalyzeThrottleProperties.class,
  PublicOriginProperties.class
})
class RepositoryAnalysisControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private RepositoryAnalysisService analysisService;
  @MockitoBean private PricingProvider pricingProvider;
  @MockitoBean private LeaderboardService leaderboardService;
  @MockitoBean private BadgeRenderer badgeRenderer;

  @Test
  void retrievesAnalysisById() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id))
        .thenReturn(
            new RepositoryAnalysisResult(
                id,
                Instant.parse("2026-05-08T20:00:00Z"),
                "https://github.com/guilu/tokenmeter",
                "https://github.com/guilu/tokenmeter.git",
                "guilu",
                "tokenmeter",
                new RepositoryScanResult(
                    1, 5, 50, List.of(), Map.of("Java", new LanguageStatistics("Java", 1, 5, 50))),
                new RepositoryTokenizationResult(
                    "o200k_base",
                    1,
                    12,
                    List.of(),
                    Map.of("Java", new LanguageTokenMetrics("Java", 1, 12))),
                List.of()));

    mockMvc
        .perform(get("/api/analyze/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/guilu/tokenmeter"))
        .andExpect(jsonPath("$.metrics.totalTokens").value(12))
        .andExpect(jsonPath("$.metrics.languages.Java.tokens").value(12))
        .andExpect(jsonPath("$.pricing").doesNotExist());
  }

  @Test
  void retrievesAnalysisWithPricingMetadataWhenPresent() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id))
        .thenReturn(sampleAnalysis(id, sampleCostEstimates(), samplePricingHandle()));

    mockMvc
        .perform(get("/api/analyze/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pricing.snapshotId").value(samplePricingId()))
        .andExpect(jsonPath("$.pricing.primarySource").value("REMOTE"))
        .andExpect(jsonPath("$.pricing.capturedAt").value("2026-05-24T18:42:11Z"));
  }

  @Test
  void returnsCostBreakdownGroupedByProviderAndModel() throws Exception {
    UUID id = UUID.randomUUID();
    when(pricingProvider.find(AiProvider.OPENAI, "gpt-4o"))
        .thenReturn(
            Optional.of(
                new ModelPricing(
                    AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00"))));
    when(analysisService.findById(id)).thenReturn(sampleAnalysis(id, sampleCostEstimates()));

    mockMvc
        .perform(get("/api/analyze/{id}/cost-breakdown", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.analysisId").value(id.toString()))
        .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/guilu/tokenmeter"))
        .andExpect(jsonPath("$.summary.totalTokens").value(25))
        .andExpect(jsonPath("$.summary.totalModels").value(1))
        .andExpect(jsonPath("$.summary.totalModes").value(3))
        .andExpect(jsonPath("$.models[0].provider").value("openai"))
        .andExpect(jsonPath("$.models[0].model").value("gpt-4o"))
        .andExpect(jsonPath("$.models[0].pricing.inputTokenPricePerMillion").value(2.50))
        .andExpect(jsonPath("$.models[0].pricing.outputTokenPricePerMillion").value(10.00))
        .andExpect(jsonPath("$.models[0].modes[0].mode").value("raw"))
        .andExpect(jsonPath("$.models[0].modes[1].mode").value("assisted"))
        .andExpect(jsonPath("$.models[0].modes[2].mode").value("agentic"))
        .andExpect(jsonPath("$.pricing").doesNotExist());
  }

  @Test
  void returnsCostBreakdownWithPricingMetadataWhenPresent() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id))
        .thenReturn(sampleAnalysis(id, sampleCostEstimates(), samplePricingHandle()));

    mockMvc
        .perform(get("/api/analyze/{id}/cost-breakdown", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pricing.snapshotId").value(samplePricingId()))
        .andExpect(jsonPath("$.pricing.primarySource").value("REMOTE"))
        .andExpect(jsonPath("$.pricing.capturedAt").value("2026-05-24T18:42:11Z"));
  }

  @Test
  void returnsPublicLeaderboardPageHtmlWithSeoMetadata() throws Exception {
    mockMvc
        .perform(get("/leaderboards"))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentType())
                    .contains(MediaType.TEXT_HTML_VALUE))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsString())
                    .contains("TokenMeter repository leaderboards"))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsString())
                    .contains("http://localhost/leaderboards"));
  }

  @Test
  void returnsDynamicPublicAnalysisHtmlWithOpenGraphMetadata() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id)).thenReturn(sampleAnalysis(id, sampleCostEstimates()));

    mockMvc
        .perform(get("/analysis/{id}", id))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentType())
                    .contains(MediaType.TEXT_HTML_VALUE))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsString())
                    .contains("TokenMeter analysis for guilu/tokenmeter"))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsString())
                    .contains(
                        "http://localhost/api/analyze/"
                            + id
                            + "/og-image.png?mode=raw&amp;v=range"))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsString())
                    .contains("url=/?analysis=" + id)
                    .doesNotContain("<script>"));
  }

  @Test
  void returnsDynamicOpenGraphPngForAnalysis() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id)).thenReturn(sampleAnalysis(id, sampleCostEstimates()));

    mockMvc
        .perform(get("/api/analyze/{id}/og-image.png", id).param("mode", "agentic"))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentType())
                    .isEqualTo(MediaType.IMAGE_PNG_VALUE))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getHeader("Cache-Control"))
                    .contains("max-age=86400"))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsByteArray())
                    .startsWith(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47}));
  }

  @Test
  void returnsEmptyCostBreakdownForAnalysisWithoutPricingData() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id)).thenReturn(sampleAnalysis(id, List.of()));

    mockMvc
        .perform(get("/api/analyze/{id}/cost-breakdown", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.totalModels").value(0))
        .andExpect(jsonPath("$.summary.totalModes").value(0))
        .andExpect(jsonPath("$.models").isEmpty());
  }

  @Test
  void mapsNonexistentAnalysisCostBreakdownToNotFound() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id)).thenThrow(new AnalysisNotFoundException(id));

    mockMvc
        .perform(get("/api/analyze/{id}/cost-breakdown", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
  }

  @Test
  void mapsMalformedAnalysisIdToBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/analyze/not-a-uuid/cost-breakdown"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void mapsNonexistentAnalysisToNotFound() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id)).thenThrow(new AnalysisNotFoundException(id));

    mockMvc
        .perform(get("/api/analyze/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
  }

  @Test
  void mapsUnknownLeaderboardCategoryToBadRequest() throws Exception {
    mockMvc
        .perform(get("/api/leaderboards").param("category", "not-a-real-category"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void leaderboardEndpointReturnsCacheControlHeader() throws Exception {
    when(leaderboardService.getLeaderboard(
            any(), any(Integer.class), any(Integer.class), any(), any(), any()))
        .thenReturn(
            new LeaderboardPageResponse(
                "most-expensive", 0, 12, 0L, 0, java.util.Map.of(), java.util.List.of()));

    mockMvc
        .perform(get("/api/leaderboards"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=30, public"));
  }

  @Test
  void returnsSvgBadgeForAnalysis() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id)).thenReturn(sampleAnalysis(id, sampleCostEstimates()));
    when(badgeRenderer.render(any())).thenReturn("<svg>badge</svg>");

    mockMvc
        .perform(get("/api/analyze/{id}/badge.svg", id))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentType())
                    .contains("image/svg+xml"))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsString())
                    .contains("<svg>"));
  }

  // ---------------------------------------------------------------------------
  // export.md endpoint tests
  // ---------------------------------------------------------------------------

  @Test
  void exportMarkdown_returns200WithContentTypeAndDisposition() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id))
        .thenReturn(sampleAnalysis(id, sampleCostEstimates(), samplePricingHandle()));

    mockMvc
        .perform(get("/api/analyze/{id}/export.md", id))
        .andExpect(status().isOk())
        .andExpect(
            result -> assertThat(result.getResponse().getContentType()).contains("text/markdown"))
        .andExpect(
            header()
                .string(
                    "Content-Disposition",
                    "attachment; filename=\"tokenmeter-guilu-tokenmeter.md\""))
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsString())
                    .contains("https://github.com/guilu/tokenmeter"));
  }

  @Test
  void exportMarkdown_bodyContainsRequiredSections() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id))
        .thenReturn(sampleAnalysis(id, sampleCostEstimates(), samplePricingHandle()));

    mockMvc
        .perform(get("/api/analyze/{id}/export.md", id))
        .andExpect(status().isOk())
        .andExpect(
            result -> {
              String body = result.getResponse().getContentAsString();
              assertThat(body).contains("25");
              assertThat(body).contains("Java");
              assertThat(body).contains("gpt-4o");
              assertThat(body).contains("v1:" + "0".repeat(64));
            });
  }

  @Test
  void exportMarkdown_withNullPricing_returns200WithNotAvailableLine() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id)).thenReturn(sampleAnalysis(id, sampleCostEstimates(), null));

    mockMvc
        .perform(get("/api/analyze/{id}/export.md", id))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentAsString())
                    .contains("Pricing snapshot: not available"));
  }

  @Test
  void exportMarkdown_unknownId_returns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id)).thenThrow(new AnalysisNotFoundException(id));

    mockMvc
        .perform(get("/api/analyze/{id}/export.md", id))
        .andExpect(status().isNotFound())
        .andExpect(
            result -> assertThat(result.getResponse().getHeader("Content-Disposition")).isNull());
  }

  private static RepositoryAnalysisResult sampleAnalysis(
      UUID id, List<ModelCostEstimate> costEstimates) {
    return sampleAnalysis(id, costEstimates, null);
  }

  private static RepositoryAnalysisResult sampleAnalysis(
      UUID id, List<ModelCostEstimate> costEstimates, PricingSnapshotHandle pricing) {
    return new RepositoryAnalysisResult(
        id,
        Instant.parse("2026-05-08T20:00:00Z"),
        "https://github.com/guilu/tokenmeter",
        "https://github.com/guilu/tokenmeter.git",
        "guilu",
        "tokenmeter",
        new RepositoryScanResult(
            2, 10, 120, List.of(), Map.of("Java", new LanguageStatistics("Java", 2, 10, 120))),
        new RepositoryTokenizationResult(
            "o200k_base",
            2,
            25,
            List.of(),
            Map.of("Java", new LanguageTokenMetrics("Java", 2, 25))),
        costEstimates,
        pricing);
  }

  private static PricingSnapshotHandle samplePricingHandle() {
    return new PricingSnapshotHandle(
        new PricingSnapshotId(samplePricingId()),
        PricingSource.REMOTE,
        Instant.parse("2026-05-24T18:42:11Z"),
        List.of());
  }

  private static String samplePricingId() {
    return "v1:" + "0".repeat(64);
  }

  private static List<ModelCostEstimate> sampleCostEstimates() {
    return List.of(
        new ModelCostEstimate(
            AiProvider.OPENAI,
            "gpt-4o",
            CostEstimationMode.RAW,
            25,
            0,
            25,
            new BigDecimal("0.000000"),
            new BigDecimal("0.000250"),
            new BigDecimal("0.000250"),
            "raw formula"),
        new ModelCostEstimate(
            AiProvider.OPENAI,
            "gpt-4o",
            CostEstimationMode.ASSISTED,
            25,
            25,
            125,
            new BigDecimal("0.000063"),
            new BigDecimal("0.001250"),
            new BigDecimal("0.001313"),
            "assisted formula"),
        new ModelCostEstimate(
            AiProvider.OPENAI,
            "gpt-4o",
            CostEstimationMode.AGENTIC,
            25,
            100,
            500,
            new BigDecimal("0.000250"),
            new BigDecimal("0.005000"),
            new BigDecimal("0.005250"),
            "agentic formula"));
  }
}
