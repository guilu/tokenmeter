package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalyzeThrottleProperties;
import dev.diegobarrioh.tokenmeter.application.repository.TrendingQuery;
import dev.diegobarrioh.tokenmeter.application.repository.TrendingSuggestionsService;
import dev.diegobarrioh.tokenmeter.application.repository.TrendingSuggestionsService.TrendingSuggestions;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepository;
import dev.diegobarrioh.tokenmeter.infrastructure.web.WebMvcConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TrendingRepositoriesController.class)
@Import({RepositoryIntakeExceptionHandler.class, WebMvcConfiguration.class})
@EnableConfigurationProperties(AnalyzeThrottleProperties.class)
class TrendingRepositoriesControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TrendingSuggestionsService service;

  @Test
  void returns200WithItemsAndCacheControlHeader() throws Exception {
    when(service.get(any()))
        .thenReturn(
            new TrendingSuggestions(
                sampleResult(), Map.of("https://github.com/acme/widget", true)));

    mockMvc
        .perform(get("/api/repositories/trending"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=900, public"))
        .andExpect(jsonPath("$.since").value("weekly"))
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items[0].fullName").value("acme/widget"))
        .andExpect(jsonPath("$.items[0].repositoryUrl").value("https://github.com/acme/widget"))
        .andExpect(jsonPath("$.items[0].stars").value(1234))
        .andExpect(jsonPath("$.items[0].language").value("Java"))
        .andExpect(jsonPath("$.items[0].analyzed").value(true));
  }

  @Test
  void omitsNullOptionalFields() throws Exception {
    TrendingRepository minimal =
        new TrendingRepository(
            "acme/bare",
            "https://github.com/acme/bare",
            null,
            null,
            5,
            1,
            null,
            Instant.parse("2026-05-20T00:00:00Z"),
            Instant.parse("2026-05-26T00:00:00Z"));
    when(service.get(any()))
        .thenReturn(
            new TrendingSuggestions(
                new TrendingRepositoriesResult(
                    List.of(minimal), Instant.parse("2026-05-27T12:00:00Z"), "weekly", null),
                Map.of()));

    mockMvc
        .perform(get("/api/repositories/trending"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].analyzed").value(false))
        .andExpect(jsonPath("$.items[0].description").doesNotExist())
        .andExpect(jsonPath("$.items[0].language").doesNotExist())
        .andExpect(jsonPath("$.items[0].sizeKb").doesNotExist())
        .andExpect(jsonPath("$.language").doesNotExist());
  }

  @Test
  void normalizesParamsBeforeDelegating() throws Exception {
    when(service.get(any())).thenReturn(new TrendingSuggestions(sampleResult(), Map.of()));

    mockMvc
        .perform(
            get("/api/repositories/trending")
                .param("since", "MONTHLY")
                .param("limit", "999")
                .param("language", "Java"))
        .andExpect(status().isOk());

    ArgumentCaptor<TrendingQuery> captor = ArgumentCaptor.forClass(TrendingQuery.class);
    verify(service).get(captor.capture());
    TrendingQuery query = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(query.since())
        .isEqualTo(TrendingQuery.Since.MONTHLY);
    org.assertj.core.api.Assertions.assertThat(query.limit()).isEqualTo(30);
    org.assertj.core.api.Assertions.assertThat(query.language()).contains("java");
  }

  @Test
  void rateLimitedSurfacesAs503() throws Exception {
    when(service.get(any()))
        .thenThrow(
            new RepositoryIntakeException(
                RepositoryIntakeErrorCode.GITHUB_RATE_LIMITED, "rate limited"));

    mockMvc
        .perform(get("/api/repositories/trending"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("GITHUB_RATE_LIMITED"));
  }

  @Test
  void upstreamUnavailableSurfacesAs503() throws Exception {
    when(service.get(any()))
        .thenThrow(
            new RepositoryIntakeException(
                RepositoryIntakeErrorCode.GITHUB_UNAVAILABLE, "github down"));

    mockMvc
        .perform(get("/api/repositories/trending"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("GITHUB_UNAVAILABLE"));
  }

  private static TrendingRepositoriesResult sampleResult() {
    TrendingRepository item =
        new TrendingRepository(
            "acme/widget",
            "https://github.com/acme/widget",
            "A widget",
            "Java",
            1234,
            56,
            789,
            Instant.parse("2026-05-20T00:00:00Z"),
            Instant.parse("2026-05-26T00:00:00Z"));
    return new TrendingRepositoriesResult(
        List.of(item), Instant.parse("2026-05-27T12:00:00Z"), "weekly", null);
  }
}
