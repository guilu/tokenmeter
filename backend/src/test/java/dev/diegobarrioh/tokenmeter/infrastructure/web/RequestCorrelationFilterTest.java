package dev.diegobarrioh.tokenmeter.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

  private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void generatesRequestIdWhenHeaderMissing() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringChain = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get("requestId"));

    filter.doFilter(request, response, chain);

    assertThat(mdcDuringChain.get()).isNotBlank();
    assertThat(response.getHeader("X-Request-Id")).isEqualTo(mdcDuringChain.get());
  }

  @Test
  void reusesSafeIncomingRequestIdHeader() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "edge-abc_123.4");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringChain = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get("requestId"));

    filter.doFilter(request, response, chain);

    assertThat(mdcDuringChain.get()).isEqualTo("edge-abc_123.4");
    assertThat(response.getHeader("X-Request-Id")).isEqualTo("edge-abc_123.4");
  }

  @Test
  void rejectsUnsafeIncomingHeaderAndGeneratesOne() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "bad value with spaces / and slashes");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringChain = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get("requestId"));

    filter.doFilter(request, response, chain);

    assertThat(mdcDuringChain.get()).isNotEqualTo("bad value with spaces / and slashes");
    assertThat(mdcDuringChain.get()).matches("[A-Za-z0-9._-]{1,64}");
  }

  @Test
  void rejectsOverlongIncomingHeader() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "a".repeat(200));
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> mdcDuringChain = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDuringChain.set(MDC.get("requestId"));

    filter.doFilter(request, response, chain);

    assertThat(mdcDuringChain.get()).hasSizeLessThanOrEqualTo(64);
  }

  @Test
  void clearsMdcAfterRequest() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {};

    filter.doFilter(request, response, chain);

    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void clearsMdcEvenWhenChainThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) -> {
          throw new ServletException("boom");
        };

    assertThatThrownBy(() -> filter.doFilter(request, response, chain))
        .isInstanceOf(ServletException.class);
    assertThat(MDC.get("requestId")).isNull();
  }
}
