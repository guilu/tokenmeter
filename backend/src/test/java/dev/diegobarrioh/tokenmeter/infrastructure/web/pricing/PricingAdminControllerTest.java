package dev.diegobarrioh.tokenmeter.infrastructure.web.pricing;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalyzeThrottleProperties;
import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshException;
import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshResult;
import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshService;
import dev.diegobarrioh.tokenmeter.infrastructure.pricing.PricingProperties;
import dev.diegobarrioh.tokenmeter.infrastructure.web.WebMvcConfiguration;
import dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer.AnalyzeRateLimitInterceptor;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(SpringExtension.class)
class PricingAdminControllerTest {

  private static final OffsetDateTime FETCHED_AT =
      OffsetDateTime.of(2026, 5, 15, 3, 0, 0, 0, ZoneOffset.UTC);

  @WebMvcTest(PricingAdminController.class)
  @Import({
    AnalyzeRateLimitInterceptor.class,
    WebMvcConfiguration.class,
    PricingExceptionHandler.class
  })
  @EnableConfigurationProperties({AnalyzeThrottleProperties.class, PricingProperties.class})
  @TestPropertySource(
      properties = {
        "tokenmeter.pricing.admin.enabled=true",
        "tokenmeter.pricing.refresh.enabled=false"
      })
  static class AdminEnabledTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PricingRefreshService refreshService;

    @Test
    void successfulRefreshReturns202AndResultBody() throws Exception {
      when(refreshService.refresh()).thenReturn(new PricingRefreshResult(FETCHED_AT, 17, 0, 0));

      mockMvc
          .perform(post("/api/admin/pricing/refresh"))
          .andExpect(status().isAccepted())
          .andExpect(jsonPath("$.updated").value(17))
          .andExpect(jsonPath("$.skipped").value(0))
          .andExpect(jsonPath("$.failed").value(0))
          .andExpect(jsonPath("$.fetchedAt").exists());
    }

    @Test
    void partialCoverageStillReturns202() throws Exception {
      when(refreshService.refresh()).thenReturn(new PricingRefreshResult(FETCHED_AT, 15, 2, 0));

      mockMvc
          .perform(post("/api/admin/pricing/refresh"))
          .andExpect(status().isAccepted())
          .andExpect(jsonPath("$.updated").value(15))
          .andExpect(jsonPath("$.skipped").value(2));
    }

    @Test
    void refreshFailureMapsToServiceUnavailableWithErrorBody() throws Exception {
      when(refreshService.refresh()).thenThrow(new PricingRefreshException("upstream 503"));

      mockMvc
          .perform(post("/api/admin/pricing/refresh"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.error").value("pricing_refresh_failed"))
          .andExpect(jsonPath("$.message").value("upstream 503"));
    }
  }

  @WebMvcTest(PricingAdminController.class)
  @Import({
    AnalyzeRateLimitInterceptor.class,
    WebMvcConfiguration.class,
    PricingExceptionHandler.class
  })
  @EnableConfigurationProperties({AnalyzeThrottleProperties.class, PricingProperties.class})
  @TestPropertySource(properties = "tokenmeter.pricing.admin.enabled=false")
  static class AdminDisabledTests {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PricingRefreshService refreshService;

    @Test
    void returnsServiceUnavailableWithoutInvokingRefreshService() throws Exception {
      mockMvc
          .perform(post("/api/admin/pricing/refresh"))
          .andExpect(status().isServiceUnavailable());
      verify(refreshService, never()).refresh();
    }
  }
}
