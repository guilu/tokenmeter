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
import dev.diegobarrioh.tokenmeter.infrastructure.web.WebMvcConfiguration;
import dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer.AnalyzeRateLimitInterceptor;
import java.math.BigDecimal;
import java.util.List;
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
  @Autowired private MockMvc mockMvc;

  @MockitoBean private PricingProvider pricingProvider;

  @Test
  void returnsConfiguredModelPricingSortedByProviderAndModel() throws Exception {
    when(pricingProvider.all())
        .thenReturn(
            List.of(
                new ModelPricing(
                    AiProvider.OPENAI, "gpt-4o", new BigDecimal("2.50"), new BigDecimal("10.00")),
                new ModelPricing(
                    AiProvider.DEEPSEEK,
                    "deepseek-chat",
                    new BigDecimal("0.27"),
                    new BigDecimal("1.10"))));

    mockMvc
        .perform(get("/api/pricing"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.models[*].provider", contains("deepseek", "openai")))
        .andExpect(jsonPath("$.models[0].model").value("deepseek-chat"))
        .andExpect(jsonPath("$.models[0].outputTokenPricePerMillion").value(1.10))
        .andExpect(jsonPath("$.models[1].inputTokenPricePerMillion").value(2.50));
  }
}
