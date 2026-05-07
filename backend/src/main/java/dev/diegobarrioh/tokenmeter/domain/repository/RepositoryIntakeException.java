package dev.diegobarrioh.tokenmeter.domain.repository;

public class RepositoryIntakeException extends RuntimeException {
  private final RepositoryIntakeErrorCode errorCode;

  public RepositoryIntakeException(RepositoryIntakeErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public RepositoryIntakeException(
      RepositoryIntakeErrorCode errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public RepositoryIntakeErrorCode errorCode() {
    return errorCode;
  }

  public static RepositoryIntakeException invalidUrl(String message) {
    return new RepositoryIntakeException(RepositoryIntakeErrorCode.INVALID_URL, message);
  }

  public static RepositoryIntakeException invalidUrl(String message, Throwable cause) {
    return new RepositoryIntakeException(RepositoryIntakeErrorCode.INVALID_URL, message, cause);
  }
}
