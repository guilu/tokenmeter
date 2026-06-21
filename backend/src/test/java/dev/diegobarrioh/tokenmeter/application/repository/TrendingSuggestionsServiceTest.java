package dev.diegobarrioh.tokenmeter.application.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.application.repository.TrendingSuggestionsService.TrendingSuggestions;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrendingSuggestionsServiceTest {

  @Mock private TrendingRepositoriesService trending;
  @Mock private AnalyzedRepositoriesPort analyzedRepositories;
  @Captor private ArgumentCaptor<java.util.Collection<String>> urlsCaptor;

  @Test
  void flagsItemsThatHaveBeenAnalyzed() {
    TrendingSuggestionsService service =
        new TrendingSuggestionsService(trending, analyzedRepositories);
    when(trending.get(any()))
        .thenReturn(
            result(
                repo("acme/widget", "https://github.com/acme/widget"),
                repo("acme/gadget", "https://github.com/acme/gadget")));
    when(analyzedRepositories.analyzedRepositoryUrls(any()))
        .thenReturn(Set.of("https://github.com/acme/widget"));

    TrendingSuggestions suggestions = service.get(TrendingQuery.fromParams("weekly", 12, null));

    assertThat(suggestions.analyzedByRepositoryUrl())
        .containsEntry("https://github.com/acme/widget", true)
        .containsEntry("https://github.com/acme/gadget", false);
  }

  @Test
  void normalizesUrlsToCanonicalFormBeforeLookup() {
    TrendingSuggestionsService service =
        new TrendingSuggestionsService(trending, analyzedRepositories);
    when(trending.get(any()))
        .thenReturn(result(repo("Acme/Widget", "https://github.com/Acme/Widget")));
    when(analyzedRepositories.analyzedRepositoryUrls(any()))
        .thenReturn(Set.of("https://github.com/acme/widget"));

    TrendingSuggestions suggestions = service.get(TrendingQuery.fromParams("weekly", 12, null));

    // The mixed-case upstream URL is matched against the canonical, lower-cased analysis URL.
    assertThat(suggestions.analyzedByRepositoryUrl())
        .containsEntry("https://github.com/Acme/Widget", true);
    org.mockito.Mockito.verify(analyzedRepositories).analyzedRepositoryUrls(urlsCaptor.capture());
    assertThat(urlsCaptor.getValue()).containsExactly("https://github.com/acme/widget");
  }

  @Test
  void treatsUnparseableUrlsAsNotAnalyzed() {
    TrendingSuggestionsService service =
        new TrendingSuggestionsService(trending, analyzedRepositories);
    when(trending.get(any())).thenReturn(result(repo("weird/repo", "not-a-valid-url")));
    when(analyzedRepositories.analyzedRepositoryUrls(any())).thenReturn(Set.of());

    TrendingSuggestions suggestions = service.get(TrendingQuery.fromParams("weekly", 12, null));

    assertThat(suggestions.analyzedByRepositoryUrl()).containsEntry("not-a-valid-url", false);
  }

  private static TrendingRepositoriesResult result(TrendingRepository... items) {
    return new TrendingRepositoriesResult(
        List.of(items), Instant.parse("2026-05-27T12:00:00Z"), "weekly", null);
  }

  private static TrendingRepository repo(String fullName, String url) {
    return new TrendingRepository(
        fullName,
        url,
        null,
        null,
        1,
        1,
        null,
        Instant.parse("2026-05-20T00:00:00Z"),
        Instant.parse("2026-05-26T00:00:00Z"));
  }
}
