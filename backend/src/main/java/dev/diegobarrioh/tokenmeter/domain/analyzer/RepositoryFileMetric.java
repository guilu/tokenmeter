package dev.diegobarrioh.tokenmeter.domain.analyzer;

import java.nio.file.Path;

public record RepositoryFileMetric(Path relativePath, String language, long lines, long bytes) {}
