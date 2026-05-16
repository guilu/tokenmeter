package dev.diegobarrioh.tokenmeter.infrastructure.web.pricing;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalyzeThrottleProperties;
import dev.diegobarrioh.tokenmeter.application.pricing.PricingProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import dev.diegobarrioh.tokenmeter.infrastructure.web.WebMvcConfiguration;
import dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer.AnalyzeRateLimitInterceptor;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PricingController.class)
@Import({AnalyzeRateLimitInterceptor.class, WebMvcConfiguration.class})
@EnableConfigurationProperties(AnalyzeThrottleProperties.class)
class PricingControllerTest {

  private static final OffsetDateTime FETCHED_AT =
      OffsetDateTime.of(2026, 5, 15, 3, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PricingProvider pricingProvider;

  @Test
  void returnsConfiguredModelPricingSortedByProviderAndModel() throws Exception {
    when(pricingProvider.snapshots())
        .thenReturn(
            List.of(
                new PricingSnapshot(
                    new ModelPricing(
                        AiProvider.OPENAI,
                        "gpt-4o",
                        new BigDecimal("2.50"),
                        new BigDecimal("10.00")),
                    PricingSource.REMOTE,
                    FETCHED_AT,
                    "gpt-4o"),
                new PricingSnapshot(
                    new ModelPricing(
                        AiProvider.DEEPSEEK,
                        "deepseek-chat",
                        new BigDecimal("0.27"),
                        new BigDecimal("1.10")),
                    PricingSource.FALLBACK,
                    FETCHED_AT,
                    null)));

    mockMvc
        .perform(get("/api/pricing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.models[*].provider", contains("deepseek", "openai")))
        .andExpect(jsonPath("$.models[0].model").value("deepseek-chat"))
        .andExpect(jsonPath("$.models[0].outputTokenPricePerMillion").value(1.10))
        .andExpect(jsonPath("$.models[1].inputTokenPricePerMillion").value(2.50))
        .andExpect(jsonPath("$.models[0].source").value("FALLBACK"))
        .andExpect(jsonPath("$.models[1].source").value("REMOTE"))
        .andExpect(jsonPath("$.models[1].externalModelId").value("gpt-4o"))
        .andExpect(jsonPath("$.models[*].fetchedAt", Matchers.everyItem(Matchers.notNullValue())))
        .andExpect(jsonPath("$.primarySource").value("mixed"))
        .andExpect(jsonPath("$.lastRefreshedAt").exists());
  }

  @Test
  void coldStartFallbackResponseReturns200WithNullLastRefreshedAt() throws Exception {
    when(pricingProvider.snapshots())
        .thenReturn(
            List.of(
                new PricingSnapshot(
                    new ModelPricing(
                        AiProvider.MISTRAL,
                        "codestral",
                        new BigDecimal("0.20"),
                        new BigDecimal("0.60")),
                    PricingSource.FALLBACK,
                    FETCHED_AT,
                    null),
                new PricingSnapshot(
                    new ModelPricing(
                        AiProvider.OPENAI,
                        "gpt-4o",
                        new BigDecimal("2.50"),
                        new BigDecimal("10.00")),
                    PricingSource.FALLBACK,
                    FETCHED_AT,
                    null)));

    mockMvc
        .perform(get("/api/pricing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastRefreshedAt").doesNotExist())
        .andExpect(jsonPath("$.primarySource").value("fallback"))
        .andExpect(jsonPath("$.models[*].source", Matchers.everyItem(Matchers.equalTo("FALLBACK"))))
        .andExpect(jsonPath("$.models[*].provider", contains("mistral", "openai")));
  }

  @Test
  void allRemoteSourceLabelsResponseAsLitellm() throws Exception {
    when(pricingProvider.snapshots())
        .thenReturn(
            List.of(
                new PricingSnapshot(
                    new ModelPricing(
                        AiProvider.OPENAI,
                        "gpt-4o",
                        new BigDecimal("2.50"),
                        new BigDecimal("10.00")),
                    PricingSource.REMOTE,
                    FETCHED_AT,
                    "gpt-4o")));

    mockMvc
        .perform(get("/api/pricing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.primarySource").value("litellm"))
        .andExpect(jsonPath("$.lastRefreshedAt").exists());
  }
}
