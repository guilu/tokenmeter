package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import jakarta.validation.constraints.NotBlank;

public record RepositoryAnalysisRequest(@NotBlank String repositoryUrl) {}
