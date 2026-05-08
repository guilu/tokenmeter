package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import dev.diegobarrioh.tokenmeter.application.analyzer.AnalysisNotFoundException;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class RepositoryIntakeExceptionHandler {
  @ExceptionHandler(RepositoryIntakeException.class)
  public ResponseEntity<RepositoryIntakeErrorResponse> handleRepositoryIntakeException(
      RepositoryIntakeException exception, HttpServletRequest request) {
    HttpStatus status = toStatus(exception.errorCode());
    return ResponseEntity.status(status)
        .body(
            new RepositoryIntakeErrorResponse(
                exception.errorCode().name(),
                exception.getMessage(),
                status.value(),
                request.getRequestURI(),
                Instant.now()));
  }

  @ExceptionHandler(AnalysisNotFoundException.class)
  public ResponseEntity<RepositoryIntakeErrorResponse> handleAnalysisNotFoundException(
      AnalysisNotFoundException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            new RepositoryIntakeErrorResponse(
                "ANALYSIS_NOT_FOUND",
                exception.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                request.getRequestURI(),
                Instant.now()));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<RepositoryIntakeErrorResponse> handleTypeMismatchException(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            new RepositoryIntakeErrorResponse(
                "INVALID_REQUEST",
                "Malformed request parameter: " + exception.getName(),
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(),
                Instant.now()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<RepositoryIntakeErrorResponse> handleValidationException(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            new RepositoryIntakeErrorResponse(
                RepositoryIntakeErrorCode.INVALID_URL.name(),
                "Repository URL is required",
                HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(),
                Instant.now()));
  }

  private static HttpStatus toStatus(RepositoryIntakeErrorCode errorCode) {
    return switch (errorCode) {
      case INVALID_URL -> HttpStatus.BAD_REQUEST;
      case REPOSITORY_NOT_ACCESSIBLE -> HttpStatus.NOT_FOUND;
      case REPOSITORY_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
      case CLONE_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
      case CLONE_FAILED -> HttpStatus.BAD_GATEWAY;
    };
  }
}
