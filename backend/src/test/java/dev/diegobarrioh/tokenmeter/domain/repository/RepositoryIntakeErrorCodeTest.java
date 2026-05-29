package dev.diegobarrioh.tokenmeter.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepositoryIntakeErrorCodeTest {

  @Test
  void enumContainsGithubRateLimited() {
    RepositoryIntakeErrorCode code = RepositoryIntakeErrorCode.GITHUB_RATE_LIMITED;
    assertThat(code.name()).isEqualTo("GITHUB_RATE_LIMITED");
  }

  @Test
  void enumContainsGithubUnavailable() {
    RepositoryIntakeErrorCode code = RepositoryIntakeErrorCode.GITHUB_UNAVAILABLE;
    assertThat(code.name()).isEqualTo("GITHUB_UNAVAILABLE");
  }

  @Test
  void existingCodeInvalidUrlStillPresent() {
    RepositoryIntakeErrorCode code = RepositoryIntakeErrorCode.INVALID_URL;
    assertThat(code.name()).isEqualTo("INVALID_URL");
  }
}
