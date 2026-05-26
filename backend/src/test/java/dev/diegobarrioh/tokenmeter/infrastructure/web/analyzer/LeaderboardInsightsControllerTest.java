package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalyzeThrottleProperties;
import dev.diegobarrioh.tokenmeter.infrastructure.web.WebMvcConfiguration;
import dev.diegobarrioh.tokenmeter.infrastructure.web.repository.RepositoryIntakeExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LeaderboardInsightsController.class)
@Import({
  RepositoryIntakeExceptionHandler.class,
  WebMvcConfiguration.class,
})
@EnableConfigurationProperties(AnalyzeThrottleProperties.class)
class LeaderboardInsightsControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private LeaderboardInsightsService insightsService;

  @Test
  void overviewReturns200WithJsonAndCacheControlHeader() throws Exception {
    when(insightsService.getOverview(any(), any(), any())).thenReturn(emptyOverview());

    mockMvc
        .perform(get("/api/leaderboards/insights/overview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRepos").value(0))
        .andExpect(jsonPath("$.totalAnalyses").value(0))
        .andExpect(jsonPath("$.costsByMode").isArray())
        .andExpect(header().string("Cache-Control", "max-age=60, public"));
  }

  @Test
  void languagesReturns200WithJsonAndCacheControlHeader() throws Exception {
    when(insightsService.getLanguages(any(), any(), any())).thenReturn(emptyLanguages());

    mockMvc
        .perform(get("/api/leaderboards/insights/languages"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.languages").isArray())
        .andExpect(jsonPath("$.totalTokensAllLanguages").value(0))
        .andExpect(header().string("Cache-Control", "max-age=300, public"));
  }

  @Test
  void languagesFilterParamsPropagatedToService() throws Exception {
    when(insightsService.getLanguages("raw", "openai", "gpt-4o")).thenReturn(emptyLanguages());

    mockMvc
        .perform(
            get("/api/leaderboards/insights/languages")
                .param("mode", "raw")
                .param("provider", "openai")
                .param("model", "gpt-4o"))
        .andExpect(status().isOk());

    verify(insightsService).getLanguages("raw", "openai", "gpt-4o");
  }

  @Test
  void languagesFilterEchoedInResponse() throws Exception {
    LeaderboardLanguagesResponse responseWithFilters =
        new LeaderboardLanguagesResponse(
            List.of(), 0L, Map.of("mode", "raw", "provider", "openai"));
    when(insightsService.getLanguages(any(), any(), any())).thenReturn(responseWithFilters);

    mockMvc
        .perform(
            get("/api/leaderboards/insights/languages")
                .param("mode", "raw")
                .param("provider", "openai"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.filters.mode").value("raw"))
        .andExpect(jsonPath("$.filters.provider").value("openai"));
  }

  @Test
  void overviewFilterParamsPropagatedToService() throws Exception {
    when(insightsService.getOverview("raw", "openai", "gpt-4o")).thenReturn(emptyOverview());

    mockMvc
        .perform(
            get("/api/leaderboards/insights/overview")
                .param("mode", "raw")
                .param("provider", "openai")
                .param("model", "gpt-4o"))
        .andExpect(status().isOk());

    verify(insightsService).getOverview("raw", "openai", "gpt-4o");
  }

  @Test
  void overviewWithEmptyDatabaseReturnsZerosNotError() throws Exception {
    when(insightsService.getOverview(any(), any(), any())).thenReturn(emptyOverview());

    mockMvc
        .perform(get("/api/leaderboards/insights/overview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRepos").value(0))
        .andExpect(jsonPath("$.totalAnalyses").value(0))
        .andExpect(jsonPath("$.totalTokens").value(0))
        .andExpect(jsonPath("$.totalBytes").value(0))
        .andExpect(jsonPath("$.costsByMode").isEmpty());
  }

  @Test
  void overviewWithInvalidModePassedThrough() throws Exception {
    when(insightsService.getOverview(eq("INVALID"), isNull(), isNull()))
        .thenReturn(emptyOverview());

    mockMvc
        .perform(get("/api/leaderboards/insights/overview").param("mode", "INVALID"))
        .andExpect(status().isOk());

    verify(insightsService).getOverview("INVALID", null, null);
  }

  @Test
  void overviewReturnsCorrectFieldsWhenDataPresent() throws Exception {
    LeaderboardOverviewResponse response =
        new LeaderboardOverviewResponse(
            5L,
            10L,
            100_000L,
            500_000L,
            List.of(new CostByModeEntry("raw", new BigDecimal("12.50"), 10L)),
            null);
    when(insightsService.getOverview(any(), any(), any())).thenReturn(response);

    mockMvc
        .perform(get("/api/leaderboards/insights/overview"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalRepos").value(5))
        .andExpect(jsonPath("$.totalAnalyses").value(10))
        .andExpect(jsonPath("$.costsByMode[0].mode").value("raw"))
        .andExpect(jsonPath("$.costsByMode[0].analysisCount").value(10));
  }

  @Test
  void languagesReturnsCorrectEntries() throws Exception {
    LeaderboardLanguagesResponse response =
        new LeaderboardLanguagesResponse(
            List.of(new LanguageInsightEntry("Java", 7_000L, 3L, new BigDecimal("70.00"))),
            10_000L,
            null);
    when(insightsService.getLanguages(any(), any(), any())).thenReturn(response);

    mockMvc
        .perform(get("/api/leaderboards/insights/languages"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.languages[0].language").value("Java"))
        .andExpect(jsonPath("$.languages[0].totalTokens").value(7000))
        .andExpect(jsonPath("$.languages[0].sharePercent").value(70.00))
        .andExpect(jsonPath("$.totalTokensAllLanguages").value(10000));
  }

  // --- helpers ---

  private static LeaderboardOverviewResponse emptyOverview() {
    return new LeaderboardOverviewResponse(0L, 0L, 0L, 0L, List.of(), null);
  }

  private static LeaderboardLanguagesResponse emptyLanguages() {
    return new LeaderboardLanguagesResponse(List.of(), 0L, null);
  }
}
