package dev.diegobarrioh.tokenmeter.infrastructure.git;

import dev.diegobarrioh.tokenmeter.application.repository.GitRepositoryCloner;
import dev.diegobarrioh.tokenmeter.domain.repository.GitHubRepositoryUrl;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.springframework.stereotype.Component;

@Component
public class JGitRepositoryCloner implements GitRepositoryCloner {
  @Override
  public void clone(GitHubRepositoryUrl repositoryUrl, Path targetDirectory) {
    try (Git ignored =
        Git.cloneRepository()
            .setURI(repositoryUrl.cloneUrl())
            .setDirectory(targetDirectory.toFile())
            .setCloneAllBranches(false)
            .setDepth(1)
            .call()) {
      // Clone is complete when JGit returns.
    } catch (TransportException exception) {
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.REPOSITORY_NOT_ACCESSIBLE,
          "Repository is private, nonexistent or not accessible",
          exception);
    } catch (GitAPIException exception) {
      throw new RepositoryIntakeException(
          RepositoryIntakeErrorCode.CLONE_FAILED, "Repository clone failed", exception);
    }
  }
}
