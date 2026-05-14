package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalyzeThrottleProperties;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AnalyzeRateLimitInterceptorTest {

  @Test
  void allowsRequestsWithinLimit() {
    AnalyzeRateLimitInterceptor interceptor = interceptorWith(3, Duration.ofSeconds(60));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpServletRequest request = requestFrom("10.0.0.1");

    assertThatNoException()
        .isThrownBy(
            () -> {
              interceptor.preHandle(request, response, new Object());
              interceptor.preHandle(request, response, new Object());
              interceptor.preHandle(request, response, new Object());
            });
  }

  @Test
  void rejectsRequestsBeyondLimit() {
    AnalyzeRateLimitInterceptor interceptor = interceptorWith(2, Duration.ofSeconds(60));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockHttpServletRequest request = requestFrom("10.0.0.2");

    interceptor.preHandle(request, response, new Object());
    interceptor.preHandle(request, response, new Object());

    assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.RATE_LIMITED);
  }

  @Test
  void isolatesCountersPerIp() {
    AnalyzeRateLimitInterceptor interceptor = interceptorWith(1, Duration.ofSeconds(60));
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(requestFrom("10.0.0.1"), response, new Object());

    assertThatNoException()
        .isThrownBy(() -> interceptor.preHandle(requestFrom("10.0.0.2"), response, new Object()));
  }

  @Test
  void extractsRightmostIpFromXForwardedFor() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1");

    String ip = AnalyzeRateLimitInterceptor.extractClientIp(request);

    org.assertj.core.api.Assertions.assertThat(ip).isEqualTo("10.0.0.1");
  }

  @Test
  void fallsBackToRemoteAddrWhenNoForwardedHeader() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.168.1.5");

    String ip = AnalyzeRateLimitInterceptor.extractClientIp(request);

    org.assertj.core.api.Assertions.assertThat(ip).isEqualTo("192.168.1.5");
  }

  @Test
  void rateLimitIsSharedAcrossRequestsFromSameProxiedIp() {
    AnalyzeRateLimitInterceptor interceptor = interceptorWith(1, Duration.ofSeconds(60));
    MockHttpServletResponse response = new MockHttpServletResponse();

    MockHttpServletRequest req1 = new MockHttpServletRequest();
    req1.addHeader("X-Forwarded-For", "spoofed, 10.0.0.1");
    MockHttpServletRequest req2 = new MockHttpServletRequest();
    req2.addHeader("X-Forwarded-For", "other-spoofed, 10.0.0.1");

    interceptor.preHandle(req1, response, new Object());

    assertThatThrownBy(() -> interceptor.preHandle(req2, response, new Object()))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.RATE_LIMITED);
  }

  private static AnalyzeRateLimitInterceptor interceptorWith(int maxRequests, Duration window) {
    return new AnalyzeRateLimitInterceptor(new AnalyzeThrottleProperties(3, maxRequests, window));
  }

  private static MockHttpServletRequest requestFrom(String ip) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(ip);
    return request;
  }
}
