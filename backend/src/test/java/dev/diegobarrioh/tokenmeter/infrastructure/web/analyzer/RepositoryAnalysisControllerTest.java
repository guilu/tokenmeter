package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisNotFoundException;
import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisService;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import dev.diegobarrioh.tokenmeter.infrastructure.web.repository.RepositoryIntakeExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RepositoryAnalysisController.class)
@Import({
  RepositoryAnalysisMapper.class,
  CostBreakdownMapper.class,
  OpenGraphImageRenderer.class,
  RepositoryIntakeExceptionHandler.class
})
class RepositoryAnalysisControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private RepositoryAnalysisService analysisService;
  @MockitoBean private PricingProvider pricingProvider;
  @MockitoBean private LeaderboardService leaderboardService;

  @Test
  void returnsStandardSuccessResponseForValidRequest() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.analyze(anyString()))
        .thenReturn(
            new RepositoryAnalysisResult(
                id,
                Instant.parse("2026-05-08T20:00:00Z"),
                "https://github.com/guilu/tokenmeter",
                "https://github.com/guilu/tokenmeter.git",
                "guilu",
                "tokenmeter",
                new RepositoryScanResult(
                    2,
                    10,
                    120,
                    List.of(),
                    Map.of("Java", new LanguageStatistics("Java", 2, 10, 120))),
                new RepositoryTokenizationResult(
                    "o200k_base",
                    2,
                    25,
                    List.of(),
                    Map.of("Java", new LanguageTokenMetrics("Java", 2, 25))),
                List.of(
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
                        "test formula"))));

    mockMvc
        .perform(
            post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"https://github.com/guilu/tokenmeter\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.createdAt").value("2026-05-08T20:00:00Z"))
        .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/guilu/tokenmeter"))
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.metrics.totalFiles").value(2))
        .andExpect(jsonPath("$.metrics.totalLines").value(10))
        .andExpect(jsonPath("$.metrics.totalBytes").value(120))
        .andExpect(jsonPath("$.metrics.tokenEncoding").value("o200k_base"))
        .andExpect(jsonPath("$.metrics.totalTokens").value(25))
        .andExpect(jsonPath("$.metrics.languages.Java.files").value(2))
        .andExpect(jsonPath("$.metrics.languages.Java.tokens").value(25))
        .andExpect(jsonPath("$.costEstimates[0].provider").value("openai"))
        .andExpect(jsonPath("$.costEstimates[0].model").value("gpt-4o"))
        .andExpect(jsonPath("$.costEstimates[0].mode").value("raw"))
        .andExpect(jsonPath("$.costEstimates[0].estimatedOutputTokens").value(25))
        .andExpect(jsonPath("$.costEstimates[0].totalCost").value(0.000250));
  }

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
        .andExpect(jsonPath("$.metrics.languages.Java.tokens").value(12));
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
        .andExpect(jsonPath("$.models[0].modes[2].mode").value("agentic"));
  }

  @Test
  void returnsPublicLeaderboardPageHtmlWithSeoMetadata() throws Exception {
    mockMvc
        .perform(get("/leaderboards").header("Host", "tokenmeter.example"))
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
                    .contains("http://tokenmeter.example/leaderboards"));
  }

  @Test
  void returnsDynamicPublicAnalysisHtmlWithOpenGraphMetadata() throws Exception {
    UUID id = UUID.randomUUID();
    when(analysisService.findById(id)).thenReturn(sampleAnalysis(id, sampleCostEstimates()));

    mockMvc
        .perform(get("/analysis/{id}", id).header("Host", "tokenmeter.example"))
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
                        "http://tokenmeter.example/api/analyze/"
                            + id
                            + "/og-image.png?mode=raw&amp;v=range"))
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(
                        result.getResponse().getContentAsString())
                    .contains("window.location.replace('/?analysis=" + id + "')"));
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
  void validatesRequiredRepositoryUrl() throws Exception {
    mockMvc
        .perform(post("/api/analyze").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_URL"))
        .andExpect(jsonPath("$.path").value("/api/analyze"));
  }

  @Test
  void mapsInvalidRepositoryUrlToBadRequest() throws Exception {
    when(analysisService.analyze(anyString()))
        .thenThrow(
            new RepositoryIntakeException(RepositoryIntakeErrorCode.INVALID_URL, "invalid url"));

    mockMvc
        .perform(
            post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"not-a-url\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_URL"));
  }

  @Test
  void mapsMissingRepositoryToNotFound() throws Exception {
    when(analysisService.analyze(anyString()))
        .thenThrow(
            new RepositoryIntakeException(
                RepositoryIntakeErrorCode.REPOSITORY_NOT_ACCESSIBLE, "not accessible"));

    mockMvc
        .perform(
            post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"https://github.com/guilu/missing\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REPOSITORY_NOT_ACCESSIBLE"));
  }

  @Test
  void mapsCloneTimeoutToGatewayTimeout() throws Exception {
    when(analysisService.analyze(anyString()))
        .thenThrow(
            new RepositoryIntakeException(RepositoryIntakeErrorCode.CLONE_TIMEOUT, "timeout"));

    mockMvc
        .perform(
            post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"https://github.com/guilu/slow\"}"))
        .andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("CLONE_TIMEOUT"));
  }

  private static RepositoryAnalysisResult sampleAnalysis(
      UUID id, List<ModelCostEstimate> costEstimates) {
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
        costEstimates);
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
