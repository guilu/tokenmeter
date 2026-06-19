package dev.diegobarrioh.tokenmeter.application.pricing.refresh;

import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates a pricing refresh: fetch via {@link PricingFetchPort}, persist via {@link
 * PricingSnapshotStorePort}, and publish a {@link PricingRefreshedEvent} on success. The whole
 * pipeline runs inside a single transaction so partial writes never reach the database.
 *
 * <p>Failures ({@link PricingRefreshException} from the fetch port, {@link DataAccessException}
 * from the store) bubble out of {@link #refresh()} after a failure-counter increment so the caller
 * — admin endpoint or scheduler — can decide whether to surface or swallow them.
 */
@Service
public class PricingRefreshService {

  private static final Logger LOG = LoggerFactory.getLogger(PricingRefreshService.class);

  private final PricingFetchPort fetcher;
  private final PricingSnapshotStorePort store;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final Counter successCounter;
  private final Counter failureCounter;

  private volatile long lastSuccessEpochSeconds;

  public PricingRefreshService(
      PricingFetchPort fetcher,
      PricingSnapshotStorePort store,
      ApplicationEventPublisher events,
      MeterRegistry meterRegistry,
      Clock clock) {
    this.fetcher = fetcher;
    this.store = store;
    this.events = events;
    this.clock = clock;
    this.successCounter =
        Counter.builder("tokenmeter.pricing.refresh.success")
            .description("Successful pricing refresh attempts")
            .register(meterRegistry);
    this.failureCounter =
        Counter.builder("tokenmeter.pricing.refresh.failure")
            .description("Failed pricing refresh attempts")
            .register(meterRegistry);
    Gauge.builder(
            "tokenmeter.pricing.refresh.last_success_timestamp_seconds",
            this,
            PricingRefreshService::getLastSuccessEpochSeconds)
        .description("Unix timestamp of the most recent successful pricing refresh")
        .strongReference(true)
        .register(meterRegistry);
  }

  /**
   * Executes a full refresh: fetch upstream, persist atomically, publish the event, update metrics.
   * Returns the resulting counts so callers can render diagnostic responses.
   *
   * @throws PricingRefreshException when the upstream fetch / mapping fails
   * @throws DataAccessException when persistence fails; the transaction is rolled back by Spring
   */
  @Transactional
  public PricingRefreshResult refresh() {
    OffsetDateTime fetchedAt = OffsetDateTime.now(clock);
    try {
      PricingFetchResult fetched = fetcher.fetchAndMap(fetchedAt);
      List<PricingSnapshot> snapshots = fetched.snapshots();
      store.replaceRemote(snapshots);

      int updated = snapshots.size();
      int skipped = fetched.skipped();
      events.publishEvent(new PricingRefreshedEvent(fetchedAt, updated, skipped));
      successCounter.increment();
      this.lastSuccessEpochSeconds = fetchedAt.toEpochSecond();
      LOG.info(
          "Pricing refresh succeeded: updated={} skipped={} fetchedAt={}",
          Integer.valueOf(updated),
          Integer.valueOf(skipped),
          fetchedAt);
      return new PricingRefreshResult(fetchedAt, updated, skipped, 0);
    } catch (PricingRefreshException ex) {
      failureCounter.increment();
      LOG.warn("Pricing refresh failed during fetch/map: {}", ex.getMessage(), ex);
      throw ex;
    } catch (DataAccessException ex) {
      failureCounter.increment();
      LOG.warn("Pricing refresh failed during persistence: {}", ex.getMessage(), ex);
      throw ex;
    }
  }

  /** Exposed for the gauge sampler and tests. */
  public long getLastSuccessEpochSeconds() {
    return lastSuccessEpochSeconds;
  }
}
