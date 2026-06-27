package dev.diegobarrioh.tokenmeter.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts a per-request correlation id into the MDC so every log line emitted while handling an HTTP
 * request carries a {@code requestId} field (parseable in Loki/Grafana without becoming a label).
 *
 * <p>The id is taken from the inbound {@code X-Request-Id} header when present and safe (so a
 * gateway/Promtail-supplied id is preserved end to end), otherwise a UUID is generated. Untrusted
 * header values are rejected to prevent log injection and unbounded cardinality. The resolved id is
 * echoed back in the response header. Runs first so the id is in scope for the whole filter chain.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

  static final String REQUEST_ID_HEADER = "X-Request-Id";
  static final String MDC_REQUEST_ID = "requestId";

  // Bounded, conservative charset: keeps the field low-cardinality-friendly and injection-safe.
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
    MDC.put(MDC_REQUEST_ID, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_REQUEST_ID);
    }
  }

  private static String resolveRequestId(String headerValue) {
    if (headerValue != null) {
      String trimmed = headerValue.trim();
      if (SAFE_REQUEST_ID.matcher(trimmed).matches()) {
        return trimmed;
      }
    }
    return UUID.randomUUID().toString();
  }
}
