package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalyzeThrottleProperties;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AnalyzeRateLimitInterceptor implements HandlerInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnalyzeRateLimitInterceptor.class);
  private static final int CLEANUP_EVERY = 500;

  private final int maxRequests;
  private final long windowMillis;
  private final ConcurrentHashMap<String, long[]> counters = new ConcurrentHashMap<>();
  private final AtomicLong requestCount = new AtomicLong();

  public AnalyzeRateLimitInterceptor(AnalyzeThrottleProperties properties) {
    this.maxRequests = properties.rateLimitRequestsPerWindow();
    this.windowMillis = properties.rateLimitWindowDuration().toMillis();
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String ip = extractClientIp(request);
    if (!allowRequest(ip)) {
      LOGGER.warn("Rate limit exceeded for IP {}", ip);
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.RATE_LIMITED,
          "Rate limit exceeded. Too many requests from your IP. Please slow down.");
    }
    return true;
  }

  private boolean allowRequest(String ip) {
    long now = System.currentTimeMillis();
    if (requestCount.incrementAndGet() % CLEANUP_EVERY == 0) {
      evictStale(now);
    }
    long[] state = counters.computeIfAbsent(ip, k -> new long[] {now, 0});
    synchronized (state) {
      if (now - state[0] >= windowMillis) {
        state[0] = now;
        state[1] = 0;
      }
      if (state[1] >= maxRequests) {
        return false;
      }
      state[1]++;
      return true;
    }
  }

  private void evictStale(long now) {
    long threshold = now - windowMillis * 2;
    counters
        .entrySet()
        .removeIf(
            e -> {
              synchronized (e.getValue()) {
                return e.getValue()[0] < threshold;
              }
            });
  }

  static String extractClientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      String[] parts = forwarded.split(",");
      return parts[parts.length - 1].trim();
    }
    return request.getRemoteAddr();
  }
}
