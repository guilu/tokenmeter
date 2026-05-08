package dev.diegobarrioh.tokenmeter.domain.tokenizer;

import java.nio.file.Path;

public record FileTokenMetrics(Path relativePath, String language, long tokens) {}
