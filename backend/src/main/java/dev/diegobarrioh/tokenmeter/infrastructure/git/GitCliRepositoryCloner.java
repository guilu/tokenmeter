package dev.diegobarrioh.tokenmeter.infrastructure.git;

import dev.diegobarrioh.tokenmeter.application.repository.GitRepositoryCloner;
import dev.diegobarrioh.tokenmeter.domain.repository.GitHubRepositoryUrl;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GitCliRepositoryCloner implements GitRepositoryCloner {
  private static final Logger LOGGER = LoggerFactory.getLogger(GitCliRepositoryCloner.class);

  @Override
  public void clone(GitHubRepositoryUrl repositoryUrl, Path targetDirectory, Duration timeout) {
    Process process = null;
    try {
      process =
          new ProcessBuilder(cloneCommand(repositoryUrl, targetDirectory))
              .redirectErrorStream(true)
              .start();
      boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!completed) {
        process.destroyForcibly();
        throw new RepositoryIntakeException(
            RepositoryIntakeErrorCode.CLONE_TIMEOUT,
            "Repository clone exceeded timeout of " + timeout.toSeconds() + " seconds");
      }
      byte[] output = process.getInputStream().readAllBytes();
      if (process.exitValue() != 0) {
        throw cloneFailure(output);
      }
    } catch (IOException exception) {
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.CLONE_FAILED, "Repository clone failed", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      if (process != null) {
        process.destroyForcibly();
      }
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.CLONE_TIMEOUT, "Repository clone was interrupted", exception);
    }
  }

  private static List<String> cloneCommand(
      GitHubRepositoryUrl repositoryUrl, Path targetDirectory) {
    return List.of(
        "git",
        "clone",
        "--depth",
        "1",
        "--single-branch",
        "--no-tags",
        "--quiet",
        repositoryUrl.cloneUrl(),
        targetDirectory.toString());
  }

  private static RepositoryIntakeException cloneFailure(byte[] output) {
    String message = new String(output, StandardCharsets.UTF_8).trim();
    String ref = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    LOGGER.warn("Git clone failed [ref={}]: {}", ref, message);
    if (isAccessError(message)) {
      return new RepositoryIntakeException(
          RepositoryIntakeErrorCode.REPOSITORY_NOT_ACCESSIBLE,
          "Repository is private, nonexistent or not accessible [ref:" + ref + "]");
    }
    return new RepositoryIntakeException(
        RepositoryIntakeErrorCode.CLONE_FAILED, "Repository clone failed [ref:" + ref + "]");
  }

  private static boolean isAccessError(String message) {
    String lower = message.toLowerCase();
    return lower.contains("repository not found")
        || lower.contains("authentication failed")
        || lower.contains("could not read from remote repository")
        || lower.contains("not found");
  }
}
