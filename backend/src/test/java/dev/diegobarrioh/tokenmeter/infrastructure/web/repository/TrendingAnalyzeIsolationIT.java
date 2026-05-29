package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diegobarrioh.tokenmeter.application.repository.TrendingRepositoriesPort;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies that a failing GitHub trending upstream does NOT break the core analyze pipeline. The
 * two features share no failure path: trending failures surface as 503 while {@code POST
 * /api/analyze} keeps validating and accepting requests independently.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TrendingAnalyzeIsolationIT {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TrendingRepositoriesPort trendingPort;

  @Test
  void trendingUpstreamFailureDoesNotBlockManualAnalyze() throws Exception {
    when(trendingPort.fetch(any()))
        .thenThrow(
            new RepositoryIntakeException(
                RepositoryIntakeErrorCode.GITHUB_UNAVAILABLE, "GitHub down"));

    // Trending endpoint degrades to 503...
    mockMvc
        .perform(get("/api/repositories/trending"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("GITHUB_UNAVAILABLE"));

    // ...while the analyze pipeline still validates input independently (400 on bad URL, not 500).
    mockMvc
        .perform(
            post("/api/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repositoryUrl\":\"not-a-valid-url\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_URL"));
  }
}
