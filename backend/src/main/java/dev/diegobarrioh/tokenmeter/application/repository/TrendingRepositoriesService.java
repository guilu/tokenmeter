package dev.diegobarrioh.tokenmeter.application.repository;

import dev.diegobarrioh.tokenmeter.domain.repository.TrendingRepositoriesResult;
import dev.diegobarrioh.tokenmeter.infrastructure.github.GitHubProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Serves trending repository results from an in-memory single-key cache, delegating misses to the
 * {@link TrendingRepositoriesPort}. The cache stores the most recent query result with a TTL;
 * errors are never cached so a transient upstream failure retries on the next request.
 *
 * <p>Cache writes use {@code compareAndSet} so concurrent cold-start misses cannot corrupt the
 * reference — a few duplicate upstream calls during the TTL window are acceptable.
 */
@Service
public class TrendingRepositoriesService {

  private final TrendingRepositoriesPort port;
  private final Clock clock;
  private final Duration ttl;
  private final AtomicReference<CachedResult> cache = new AtomicReference<>();

  @Autowired
  public TrendingRepositoriesService(
      TrendingRepositoriesPort port, Clock clock, GitHubProperties properties) {
    this(port, clock, properties.trending().cacheTtl());
  }

  TrendingRepositoriesService(TrendingRepositoriesPort port, Clock clock, Duration ttl) {
    this.port = port;
    this.clock = clock;
    this.ttl = ttl;
  }

  /**
   * Returns trending repositories for the given query, serving from cache when a non-expired entry
   * matches the same query key. On a miss the port is invoked and the result cached with an expiry
   * of {@code now + ttl}. Upstream exceptions propagate unchanged and are not cached.
   */
  public TrendingRepositoriesResult get(TrendingQuery query) {
    CachedResult current = cache.get();
    Instant now = clock.instant();
    if (current != null && current.key().equals(query) && now.isBefore(current.expiresAt())) {
      return current.value();
    }
    TrendingRepositoriesResult fresh = port.fetch(query);
    cache.compareAndSet(current, new CachedResult(query, fresh, now.plus(ttl)));
    return fresh;
  }

  private record CachedResult(TrendingQuery key, TrendingRepositoriesResult value, Instant expiresAt) {}
}
