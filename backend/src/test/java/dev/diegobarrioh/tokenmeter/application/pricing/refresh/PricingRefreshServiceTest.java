package dev.diegobarrioh.tokenmeter.application.pricing.refresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.pricing.ModelPricing;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PricingRefreshServiceTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2026-05-15T03:00:00Z");
  private static final OffsetDateTime FETCHED_AT =
      OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

  @Mock private PricingFetchPort fetcher;
  @Mock private PricingSnapshotStorePort store;
  @Mock private ApplicationEventPublisher events;

  private SimpleMeterRegistry meterRegistry;
  private PricingRefreshService service;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
    service = new PricingRefreshService(fetcher, store, events, meterRegistry, clock);
  }

  @Test
  void successPathPersistsSnapshotsPublishesEventAndIncrementsSuccessCounter() {
    List<PricingSnapshot> snapshots =
        List.of(
            snapshot(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00"),
            snapshot(AiProvider.ANTHROPIC, "claude-opus-4-7", "15.00", "75.00"));
    when(fetcher.fetchAndMap(any(OffsetDateTime.class))).thenReturn(snapshots);
    when(fetcher.configuredMappingCount()).thenReturn(3);

    PricingRefreshResult result = service.refresh();

    assertThat(result.fetchedAt()).isEqualTo(FETCHED_AT);
    assertThat(result.updated()).isEqualTo(2);
    assertThat(result.skipped()).isEqualTo(1);
    assertThat(result.failed()).isZero();

    verify(store, times(1)).replaceRemote(snapshots);

    ArgumentCaptor<PricingRefreshedEvent> eventCaptor =
        ArgumentCaptor.forClass(PricingRefreshedEvent.class);
    verify(events).publishEvent(eventCaptor.capture());
    PricingRefreshedEvent event = eventCaptor.getValue();
    assertThat(event.fetchedAt()).isEqualTo(FETCHED_AT);
    assertThat(event.updated()).isEqualTo(2);
    assertThat(event.skipped()).isEqualTo(1);

    assertThat(counter("tokenmeter.pricing.refresh.success")).isEqualTo(1.0);
    assertThat(counter("tokenmeter.pricing.refresh.failure")).isZero();
    assertThat(service.getLastSuccessEpochSeconds()).isEqualTo(FIXED_INSTANT.getEpochSecond());
  }

  @Test
  void skippedCountNeverGoesNegativeWhenMappingCountIsSmallerThanUpdated() {
    List<PricingSnapshot> snapshots =
        List.of(snapshot(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00"));
    when(fetcher.fetchAndMap(any(OffsetDateTime.class))).thenReturn(snapshots);
    when(fetcher.configuredMappingCount()).thenReturn(0);

    PricingRefreshResult result = service.refresh();

    assertThat(result.skipped()).isZero();
  }

  @Test
  void fetchFailureLeavesStoreUntouchedAndIncrementsFailureCounter() {
    PricingRefreshException upstream = new PricingRefreshException("upstream timeout");
    when(fetcher.fetchAndMap(any(OffsetDateTime.class))).thenThrow(upstream);

    assertThatThrownBy(() -> service.refresh()).isSameAs(upstream);

    verify(store, never()).replaceRemote(any());
    verify(events, never()).publishEvent(any());
    assertThat(counter("tokenmeter.pricing.refresh.failure")).isEqualTo(1.0);
    assertThat(counter("tokenmeter.pricing.refresh.success")).isZero();
    assertThat(service.getLastSuccessEpochSeconds()).isZero();
  }

  @Test
  void persistenceFailurePropagatesAndIncrementsFailureCounter() {
    List<PricingSnapshot> snapshots =
        List.of(snapshot(AiProvider.OPENAI, "gpt-4o", "2.50", "10.00"));
    when(fetcher.fetchAndMap(any(OffsetDateTime.class))).thenReturn(snapshots);
    DataAccessException dbError = new DataIntegrityViolationException("conflict");
    doThrow(dbError).when(store).replaceRemote(snapshots);

    assertThatThrownBy(() -> service.refresh()).isSameAs(dbError);

    verify(events, never()).publishEvent(any());
    assertThat(counter("tokenmeter.pricing.refresh.failure")).isEqualTo(1.0);
    assertThat(counter("tokenmeter.pricing.refresh.success")).isZero();
  }

  private double counter(String name) {
    return meterRegistry.counter(name).count();
  }

  private static PricingSnapshot snapshot(
      AiProvider provider, String model, String inputPrice, String outputPrice) {
    return new PricingSnapshot(
        new ModelPricing(provider, model, new BigDecimal(inputPrice), new BigDecimal(outputPrice)),
        PricingSource.REMOTE,
        FETCHED_AT,
        model);
  }
}
