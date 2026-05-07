package dev.diegobarrioh.tokenmeter.application.repository;

public record RepositoryIntakeResult(
    String repositoryUrl,
    String cloneUrl,
    String owner,
    String name,
    long totalBytes,
    long fileCount,
    boolean cleanedUp) {}
