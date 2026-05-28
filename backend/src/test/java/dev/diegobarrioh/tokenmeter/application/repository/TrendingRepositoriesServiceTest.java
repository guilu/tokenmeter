package dev.diegobarrioh.tokenmeter.application.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeErrorCode;
import dev.diegobarrioh.tokenmeter.domain.repository.RepositoryIntakeException;
import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrendingRepositoriesServiceTest {

  private static final Instant BASE_TIME = Instant.parse("2026-05-27T12:00:00Z");
  private static final Duration TTL = Duration.ofMinutes(30);

  private TrendingRepositoriesPort port;
  private Clock fixedClock;

  @BeforeEach
  void setUp() {
    port = mock(TrendingRepositoriesPort.class);
    fixedClock = Clock.fixed(BASE_TIME, ZoneOffset.UTC);
  }

  @Test
  void cacheHitDoesNotInvokePortSecondTime() {
    TrendingRepositoriesService service = new TrendingRepositoriesService(port, fixedClock, TTL);
    when(port.fetch(any())).thenReturn(sampleResult());

    TrendingQuery query = TrendingQuery.defaults();
    service.get(query);
    service.get(query); // second call should hit cache

    verify(port, times(1)).fetch(any());
  }

  @Test
  void cacheMissInvokesPort() {
    TrendingRepositoriesService service = new TrendingRepositoriesService(port, fixedClock, TTL);
    when(port.fetch(any())).thenReturn(sampleResult());

    TrendingQuery query = TrendingQuery.defaults();
    TrendingRepositoriesResult result = service.get(query);

    verify(port, times(1)).fetch(any());
    assertThat(result.items()).isEmpty();
  }

  @Test
  void ttlExpiredTriggersReFetch() {
    // First clock is at BASE_TIME, second clock is past TTL
    Clock[] clocks = {
      Clock.fixed(BASE_TIME, ZoneOffset.UTC),
      Clock.fixed(BASE_TIME.plus(TTL).plusSeconds(1), ZoneOffset.UTC)
    };
    AtomicInteger clockIdx = new AtomicInteger(0);

    // We need a clock that returns different instants on successive calls.
    // Use a real implementation that delegates to an array.
    Clock advancingClock =
        new Clock() {
          @Override
          public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            int idx = Math.min(clockIdx.getAndIncrement(), clocks.length - 1);
            return clocks[idx].instant();
          }
        };

    TrendingRepositoriesService service =
        new TrendingRepositoriesService(port, advancingClock, TTL);
    when(port.fetch(any())).thenReturn(sampleResult());

    service.get(TrendingQuery.defaults()); // miss → fetches, stores with expiry = BASE + TTL
    service.get(TrendingQuery.defaults()); // now clock returns BASE+TTL+1 → expired → re-fetch

    verify(port, times(2)).fetch(any());
  }

  @Test
  void differentQueryKeyLastWriteWinsNoException() {
    TrendingRepositoriesService service = new TrendingRepositoriesService(port, fixedClock, TTL);
    when(port.fetch(any())).thenReturn(sampleResult());

    service.get(TrendingQuery.fromParams("weekly", 12, null));
    service.get(TrendingQuery.fromParams("monthly", 5, "java"));

    // Both calls should succeed without exception; port called twice (cache keyed on v1 single-key)
    verify(port, times(2)).fetch(any());
  }

  @Test
  void concurrentMissesDoNotThrow() throws Exception {
    TrendingRepositoriesService service = new TrendingRepositoriesService(port, fixedClock, TTL);
    when(port.fetch(any())).thenReturn(sampleResult());

    int threads = 8;
    CountDownLatch startGate = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(threads);
    var futures = new java.util.ArrayList<Future<TrendingRepositoriesResult>>();

    for (int i = 0; i < threads; i++) {
      futures.add(
          executor.submit(
              () -> {
                startGate.await();
                return service.get(TrendingQuery.defaults());
              }));
    }

    startGate.countDown(); // release all threads at once

    for (var future : futures) {
      assertThat(future.get()).isNotNull(); // no exception
    }
    executor.shutdown();

    // Port called at least once, at most threads times
    int callCount = (int) org.mockito.Mockito.mockingDetails(port).getInvocations().stream()
        .filter(inv -> inv.getMethod().getName().equals("fetch"))
        .count();
    assertThat(callCount).isGreaterThanOrEqualTo(1);
  }

  @Test
  void errorNotCachedSecondCallRetriesPort() {
    TrendingRepositoriesService service = new TrendingRepositoriesService(port, fixedClock, TTL);
    RepositoryIntakeException error =
        new RepositoryIntakeException(
            RepositoryIntakeErrorCode.GITHUB_UNAVAILABLE, "GitHub unavailable");
    when(port.fetch(any())).thenThrow(error).thenReturn(sampleResult());

    TrendingQuery query = TrendingQuery.defaults();

    // First call throws
    assertThatThrownBy(() -> service.get(query)).isInstanceOf(RepositoryIntakeException.class);

    // Second call succeeds because error was not cached
    TrendingRepositoriesResult result = service.get(query);
    assertThat(result).isNotNull();

    verify(port, times(2)).fetch(any());
  }

  private static TrendingRepositoriesResult sampleResult() {
    return new TrendingRepositoriesResult(List.of(), Instant.parse("2026-05-27T12:00:00Z"), "weekly", null);
  }
}
