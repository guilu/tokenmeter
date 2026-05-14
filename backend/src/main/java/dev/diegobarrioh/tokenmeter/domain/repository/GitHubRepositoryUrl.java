package dev.diegobarrioh.tokenmeter.domain.repository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

public record GitHubRepositoryUrl(String owner, String name) {
  private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9_.-]+");

  public GitHubRepositoryUrl {
    owner = requireValidSegment(owner, "owner");
    name = requireValidSegment(stripGitSuffix(name), "repository");
  }

  public static GitHubRepositoryUrl parse(String rawUrl) {
    if (rawUrl == null || rawUrl.isBlank()) {
      throw RepositoryIntakeException.invalidUrl("Repository URL is required");
    }

    URI uri;
    try {
      uri = new URI(rawUrl.trim()).parseServerAuthority();
    } catch (URISyntaxException exception) {
      throw RepositoryIntakeException.invalidUrl("Repository URL is malformed", exception);
    }

    if (uri.getUserInfo() != null) {
      throw RepositoryIntakeException.invalidUrl("Repository URL must not contain credentials");
    }

    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw RepositoryIntakeException.invalidUrl("Only HTTPS GitHub repository URLs are supported");
    }

    if (!"github.com".equalsIgnoreCase(uri.getHost())) {
      throw RepositoryIntakeException.invalidUrl("Only github.com repository URLs are supported");
    }

    if (uri.getQuery() != null || uri.getFragment() != null) {
      throw RepositoryIntakeException.invalidUrl(
          "Repository URL cannot contain query parameters or fragments");
    }

    String[] segments = uri.getPath().replaceFirst("^/", "").split("/");
    if (segments.length != 2) {
      throw RepositoryIntakeException.invalidUrl(
          "Repository URL must use /owner/repository format");
    }

    return new GitHubRepositoryUrl(segments[0], segments[1]);
  }

  public String normalizedUrl() {
    return "https://github.com/" + owner + "/" + name;
  }

  public String cloneUrl() {
    return normalizedUrl() + ".git";
  }

  private static String requireValidSegment(String value, String label) {
    if (value == null || value.isBlank() || !SEGMENT.matcher(value).matches()) {
      throw RepositoryIntakeException.invalidUrl("Invalid GitHub " + label + " segment");
    }
    if (value.equals(".") || value.equals("..")) {
      throw RepositoryIntakeException.invalidUrl("Invalid GitHub " + label + " segment");
    }
    return value.toLowerCase(Locale.ROOT);
  }

  private static String stripGitSuffix(String value) {
    if (value != null && value.endsWith(".git")) {
      return value.substring(0, value.length() - 4);
    }
    return value;
  }
}
