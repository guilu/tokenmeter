package dev.diegobarrioh.tokenmeter.application.repository;

import dev.diegobarrioh.tokenmeter.domain.repository.GitHubRepositoryUrl;
import java.nio.file.Path;
import java.time.Duration;

public interface GitRepositoryCloner {
  void clone(GitHubRepositoryUrl repositoryUrl, Path targetDirectory, Duration timeout);
}
