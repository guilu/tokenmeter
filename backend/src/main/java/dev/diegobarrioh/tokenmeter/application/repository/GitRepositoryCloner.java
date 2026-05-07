package dev.diegobarrioh.tokenmeter.application.repository;

import dev.diegobarrioh.tokenmeter.domain.repository.GitHubRepositoryUrl;
import java.nio.file.Path;

public interface GitRepositoryCloner {
  void clone(GitHubRepositoryUrl repositoryUrl, Path targetDirectory);
}
