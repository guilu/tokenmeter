package dev.diegobarrioh.tokenmeter.infrastructure.web.repository;

import java.time.Instant;

public record RepositoryIntakeErrorResponse(
    String code, String message, int status, String path, Instant timestamp) {}
