package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import jakarta.validation.constraints.NotBlank;

public record RepositoryIntakeRequest(@NotBlank String repositoryUrl) {}
