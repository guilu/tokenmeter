package dev.diegobarrioh.tokenmeter.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TrendingRepositoryTest {

  @Test
  void constructorSetsAllFields() {
    Instant now = Instant.parse("2026-05-27T10:00:00Z");
    TrendingRepository repo =
        new TrendingRepository(
            "torvalds/linux",
            "https://github.com/torvalds/linux",
            "Linux kernel source tree",
            "C",
            176000,
            10500,
            1073741824,
            now,
            now,
            null);

    assertThat(repo.fullName()).isEqualTo("torvalds/linux");
    assertThat(repo.repositoryUrl()).isEqualTo("https://github.com/torvalds/linux");
    assertThat(repo.description()).isEqualTo("Linux kernel source tree");
    assertThat(repo.language()).isEqualTo("C");
    assertThat(repo.stars()).isEqualTo(176000);
    assertThat(repo.forks()).isEqualTo(10500);
    assertThat(repo.sizeKb()).isEqualTo(1073741824);
    assertThat(repo.createdAt()).isEqualTo(now);
    assertThat(repo.updatedAt()).isEqualTo(now);
    assertThat(repo.starsThisPeriod()).isNull();
  }

  @Test
  void descriptionAcceptsNull() {
    Instant now = Instant.now();
    TrendingRepository repo =
        new TrendingRepository(
            "owner/repo",
            "https://github.com/owner/repo",
            null,
            "Java",
            100,
            10,
            512,
            now,
            now,
            null);

    assertThat(repo.description()).isNull();
  }

  @Test
  void languageAcceptsNull() {
    Instant now = Instant.now();
    TrendingRepository repo =
        new TrendingRepository(
            "owner/repo",
            "https://github.com/owner/repo",
            "Some description",
            null,
            100,
            10,
            512,
            now,
            now,
            null);

    assertThat(repo.language()).isNull();
  }

  @Test
  void sizeKbAcceptsNull() {
    Instant now = Instant.now();
    TrendingRepository repo =
        new TrendingRepository(
            "owner/repo",
            "https://github.com/owner/repo",
            "Some description",
            "TypeScript",
            100,
            10,
            null,
            now,
            now,
            null);

    assertThat(repo.sizeKb()).isNull();
  }

  @Test
  void starsThisPeriodAcceptsNull() {
    Instant now = Instant.now();
    TrendingRepository repo =
        new TrendingRepository(
            "owner/repo",
            "https://github.com/owner/repo",
            "Some description",
            "TypeScript",
            100,
            10,
            512,
            now,
            now,
            null);

    assertThat(repo.starsThisPeriod()).isNull();
  }

  @Test
  void starsThisPeriodAcceptsValue() {
    Instant now = Instant.now();
    TrendingRepository repo =
        new TrendingRepository(
            "owner/repo",
            "https://github.com/owner/repo",
            "Some description",
            "TypeScript",
            100,
            10,
            512,
            now,
            now,
            42);

    assertThat(repo.starsThisPeriod()).isEqualTo(42);
  }
}
