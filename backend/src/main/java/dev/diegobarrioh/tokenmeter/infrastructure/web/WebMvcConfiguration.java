package dev.diegobarrioh.tokenmeter.infrastructure.web;

import dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer.AnalyzeRateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

  private final AnalyzeRateLimitInterceptor rateLimitInterceptor;

  public WebMvcConfiguration(AnalyzeRateLimitInterceptor rateLimitInterceptor) {
    this.rateLimitInterceptor = rateLimitInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // The interceptor protects ONLY the submission endpoints. The polling endpoint
    // `/api/analyze/jobs/{jobId}` and the analysis-read endpoint `/api/analyze/{id}` MUST NOT be
    // rate-limited (clients poll at ~1.5 s and read by id repeatedly from public OG renders).
    registry
        .addInterceptor(rateLimitInterceptor)
        .addPathPatterns("/api/analyze", "/api/repositories/intake")
        .excludePathPatterns("/api/analyze/jobs/**", "/api/analyze/*");
  }
}
