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
    registry
        .addInterceptor(rateLimitInterceptor)
        .addPathPatterns("/api/analyze", "/api/repositories/intake");
  }
}
