package dev.diegobarrioh.tokenmeter.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GitHubRepositoryUrlTest {
  @Test
  void acceptsValidGitHubUrl() {
    GitHubRepositoryUrl url = GitHubRepositoryUrl.parse("https://github.com/guilu/tokenmeter");

    assertThat(url.owner()).isEqualTo("guilu");
    assertThat(url.name()).isEqualTo("tokenmeter");
    assertThat(url.normalizedUrl()).isEqualTo("https://github.com/guilu/tokenmeter");
    assertThat(url.cloneUrl()).isEqualTo("https://github.com/guilu/tokenmeter.git");
  }

  @Test
  void acceptsGitSuffix() {
    GitHubRepositoryUrl url = GitHubRepositoryUrl.parse("https://github.com/guilu/tokenmeter.git");

    assertThat(url.normalizedUrl()).isEqualTo("https://github.com/guilu/tokenmeter");
  }

  @Test
  void rejectsNonGitHubUrl() {
    assertThatThrownBy(() -> GitHubRepositoryUrl.parse("https://gitlab.com/guilu/tokenmeter"))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.INVALID_URL);
  }

  @Test
  void rejectsMalformedUrl() {
    assertThatThrownBy(() -> GitHubRepositoryUrl.parse("not-a-url"))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.INVALID_URL);
  }

  @Test
  void rejectsUrlWithExtraPath() {
    assertThatThrownBy(
            () -> GitHubRepositoryUrl.parse("https://github.com/guilu/tokenmeter/issues"))
        .isInstanceOf(RepositoryIntakeException.class)
        .extracting("errorCode")
        .isEqualTo(RepositoryIntakeErrorCode.INVALID_URL);
  }
}
