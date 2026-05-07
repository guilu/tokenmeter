package dev.diegobarrioh.tokenmeter.domain.repository;

public enum RepositoryIntakeErrorCode {
  INVALID_URL,
  REPOSITORY_NOT_ACCESSIBLE,
  REPOSITORY_TOO_LARGE,
  CLONE_TIMEOUT,
  CLONE_FAILED
}
