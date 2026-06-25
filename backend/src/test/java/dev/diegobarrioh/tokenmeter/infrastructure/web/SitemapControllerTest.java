package dev.diegobarrioh.tokenmeter.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalyzeThrottleProperties;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardJpaRepository;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardRow;
import dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer.AnalyzeRateLimitInterceptor;
import dev.diegobarrioh.tokenmeter.infrastructure.web.repository.RepositoryIntakeExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SitemapController.class)
@Import({
  RepositoryIntakeExceptionHandler.class,
  AnalyzeRateLimitInterceptor.class,
  WebMvcConfiguration.class,
})
@EnableConfigurationProperties({PublicOriginProperties.class, AnalyzeThrottleProperties.class})
class SitemapControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private LeaderboardJpaRepository leaderboardJpaRepository;

  private static final String TEST_ORIGIN = "http://localhost";

  // ---- /sitemap.xml ----

  @Test
  void sitemapReturns200WithApplicationXmlContentType() throws Exception {
    when(leaderboardJpaRepository.findMostExpensive(
            isNull(), isNull(), isNull(), anyInt(), anyLong()))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/sitemap.xml"))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertThat(result.getResponse().getContentType()).contains("application/xml"));
  }

  @Test
  void sitemapContainsThreeStaticUrlsWithConfiguredOrigin() throws Exception {
    when(leaderboardJpaRepository.findMostExpensive(
            isNull(), isNull(), isNull(), anyInt(), anyLong()))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/sitemap.xml"))
        .andExpect(status().isOk())
        .andExpect(
            result -> {
              String body = result.getResponse().getContentAsString();
              assertThat(body).contains("<urlset");
              assertThat(body).contains("<loc>" + TEST_ORIGIN + "/</loc>");
              assertThat(body).contains("<loc>" + TEST_ORIGIN + "/models</loc>");
              assertThat(body).contains("<loc>" + TEST_ORIGIN + "/leaderboards</loc>");
            });
  }

  @Test
  void sitemapContainsAnalysisUrlWithLastmodWhenAnalysesExist() throws Exception {
    UUID analysisId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    Instant createdAt = Instant.parse("2026-03-15T10:30:00Z");
    LeaderboardRow row = stubLeaderboardRow(analysisId, createdAt);

    when(leaderboardJpaRepository.findMostExpensive(
            isNull(), isNull(), isNull(), anyInt(), anyLong()))
        .thenReturn(List.of(row));

    mockMvc
        .perform(get("/sitemap.xml"))
        .andExpect(status().isOk())
        .andExpect(
            result -> {
              String body = result.getResponse().getContentAsString();
              assertThat(body)
                  .contains("<loc>" + TEST_ORIGIN + "/analysis/" + analysisId + "</loc>");
              assertThat(body).contains("<lastmod>2026-03-15</lastmod>");
            });
  }

  @Test
  void sitemapWithEmptyDatabaseContainsOnlyStaticUrls() throws Exception {
    when(leaderboardJpaRepository.findMostExpensive(
            isNull(), isNull(), isNull(), anyInt(), anyLong()))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/sitemap.xml"))
        .andExpect(status().isOk())
        .andExpect(
            result -> {
              String body = result.getResponse().getContentAsString();
              assertThat(body).doesNotContain("/analysis/");
              assertThat(body).contains("</urlset>");
            });
  }

  @Test
  void sitemapHasCacheControlHeader() throws Exception {
    when(leaderboardJpaRepository.findMostExpensive(
            isNull(), isNull(), isNull(), anyInt(), anyLong()))
        .thenReturn(List.of());

    mockMvc
        .perform(get("/sitemap.xml"))
        .andExpect(status().isOk())
        .andExpect(
            result ->
                assertThat(result.getResponse().getHeader("Cache-Control"))
                    .contains("max-age=3600")
                    .contains("public"));
  }

  // ---- /robots.txt ----

  @Test
  void robotsTxtReturns200WithTextPlainContentType() throws Exception {
    mockMvc
        .perform(get("/robots.txt"))
        .andExpect(status().isOk())
        .andExpect(
            result -> assertThat(result.getResponse().getContentType()).contains("text/plain"));
  }

  @Test
  void robotsTxtContainsUserAgentAndSitemapWithConfiguredOrigin() throws Exception {
    mockMvc
        .perform(get("/robots.txt"))
        .andExpect(status().isOk())
        .andExpect(
            result -> {
              String body = result.getResponse().getContentAsString();
              assertThat(body).contains("User-agent: *");
              assertThat(body).contains("Allow: /");
              assertThat(body).contains("Sitemap: " + TEST_ORIGIN + "/sitemap.xml");
            });
  }

  @Test
  void robotsTxtHasCacheControlHeader() throws Exception {
    mockMvc
        .perform(get("/robots.txt"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "max-age=86400, public"));
  }

  // ---- helpers ----

  private static LeaderboardRow stubLeaderboardRow(UUID id, Instant createdAt) {
    return new LeaderboardRow() {
      @Override
      public UUID getId() {
        return id;
      }

      @Override
      public String getRepositoryUrl() {
        return "https://github.com/example/repo";
      }

      @Override
      public String getOwnerName() {
        return "example";
      }

      @Override
      public String getRepositoryName() {
        return "repo";
      }

      @Override
      public Instant getCreatedAt() {
        return createdAt;
      }

      @Override
      public long getTotalFiles() {
        return 1L;
      }

      @Override
      public long getTotalLines() {
        return 10L;
      }

      @Override
      public long getTotalBytes() {
        return 100L;
      }

      @Override
      public long getTotalTokens() {
        return 50L;
      }

      @Override
      public long getAnalysisCount() {
        return 1L;
      }

      @Override
      public String getProvider() {
        return "openai";
      }

      @Override
      public String getModel() {
        return "gpt-4o";
      }

      @Override
      public String getMode() {
        return "RAW";
      }

      @Override
      public BigDecimal getTotalCost() {
        return new BigDecimal("0.001000");
      }

      @Override
      public String getPricingSnapshotId() {
        return null;
      }

      @Override
      public String getPricingPrimarySource() {
        return null;
      }

      @Override
      public Instant getPricingCapturedAt() {
        return null;
      }

      @Override
      public String getDominantLanguage() {
        return "Java";
      }
    };
  }
}
