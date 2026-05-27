package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

class RepositoryIntakeExceptionHandlerTest {

  private final RepositoryIntakeExceptionHandler handler = new RepositoryIntakeExceptionHandler();

  @Test
  void githubRateLimitedMapsTo503() {
    HttpServletRequest request = new MockHttpServletRequest("GET", "/api/repositories/trending");
    RepositoryIntakeException exception =
        new RepositoryIntakeException(
            RepositoryIntakeErrorCode.GITHUB_RATE_LIMITED, "GitHub rate limit exceeded");

    ResponseEntity<RepositoryIntakeErrorResponse> response =
        handler.handleRepositoryIntakeException(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("GITHUB_RATE_LIMITED");
  }

  @Test
  void githubUnavailableMapsTo503() {
    HttpServletRequest request = new MockHttpServletRequest("GET", "/api/repositories/trending");
    RepositoryIntakeException exception =
        new RepositoryIntakeException(
            RepositoryIntakeErrorCode.GITHUB_UNAVAILABLE, "GitHub Search API unavailable");

    ResponseEntity<RepositoryIntakeErrorResponse> response =
        handler.handleRepositoryIntakeException(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("GITHUB_UNAVAILABLE");
  }

  @Test
  void existingInvalidUrlCodeStillMapsTo400() {
    HttpServletRequest request = new MockHttpServletRequest("POST", "/api/analyze");
    RepositoryIntakeException exception =
        new RepositoryIntakeException(RepositoryIntakeErrorCode.INVALID_URL, "invalid url");

    ResponseEntity<RepositoryIntakeErrorResponse> response =
        handler.handleRepositoryIntakeException(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().code()).isEqualTo("INVALID_URL");
  }
}
