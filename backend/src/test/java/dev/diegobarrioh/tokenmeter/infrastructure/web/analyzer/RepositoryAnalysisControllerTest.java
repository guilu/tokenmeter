package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisService;
import dev.diegobarrioh.tokenmeter.domain.analyzer.LanguageStatistics;
import dev.diegobarrioh.tokenmeter.domain.analyzer.RepositoryScanResult;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.RepositoryTokenizationResult;
import dev.diegobarrioh.tokenmeter.infrastructure.web.repository.RepositoryIntakeExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RepositoryAnalysisController.class)
@Import({RepositoryAnalysisMapper.class, RepositoryIntakeExceptionHandler.class})
class RepositoryAnalysisControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private RepositoryAnalysisService analysisService;

  @Test
  void returnsStandardSuccessResponseForValidRequest() throws Exception {
    when(analysisService.analyze(anyString()))
        .thenReturn(
            new RepositoryAnalysisResult(
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
                    Map.of("Java", new LanguageTokenMetrics("Java", 2, 25)))));

    mockMvc
        .perform(
            post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"https://github.com/guilu/tokenmeter\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/guilu/tokenmeter"))
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.metrics.totalFiles").value(2))
        .andExpect(jsonPath("$.metrics.totalLines").value(10))
        .andExpect(jsonPath("$.metrics.totalBytes").value(120))
        .andExpect(jsonPath("$.metrics.tokenEncoding").value("o200k_base"))
        .andExpect(jsonPath("$.metrics.totalTokens").value(25))
        .andExpect(jsonPath("$.metrics.languages.Java.files").value(2))
        .andExpect(jsonPath("$.metrics.languages.Java.tokens").value(25));
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
}
