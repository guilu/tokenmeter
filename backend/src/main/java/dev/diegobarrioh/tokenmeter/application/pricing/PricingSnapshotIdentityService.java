package dev.diegobarrioh.tokenmeter.application.pricing;

import dev.diegobarrioh.tokenmeter.application.pricing.refresh.PricingRefreshedEvent;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotHandle;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshotId;
import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Canonicalises the active {@link PricingSnapshot} list and exposes a deterministic, content-derived
 * {@link PricingSnapshotId}. The id is cached per refresh cycle; a {@link PricingRefreshedEvent}
 * invalidates the cache so the next read recomputes from the post-refresh snapshot.
 *
 * <p>Canonical input (UTF-8, no BOM, fed to SHA-256):
 *
 * <pre>
 * for each snapshot sorted by (provider.configKey ASC, model.toLowerCase(Locale.ROOT) ASC):
 *   configKey \t lowercaseTrimmedModel \t
 *   inputPricePerMillion.setScale(6, HALF_UP).toPlainString() \t
 *   outputPricePerMillion.setScale(6, HALF_UP).toPlainString() \t
 *   source.name() \n
 * </pre>
 *
 * {@code externalModelId} and {@code fetchedAt} are deliberately excluded so two refreshes that
 * produce the same prices collapse to the same id.
 */
@Service
public class PricingSnapshotIdentityService {

  private static final Comparator<PricingSnapshot> CANONICAL_ORDER =
      Comparator.comparing((PricingSnapshot s) -> s.provider().configKey())
          .thenComparing(s -> s.model().toLowerCase(Locale.ROOT).trim());

  private final PricingProvider pricingProvider;
  private final Clock clock;
  private final AtomicReference<PricingSnapshotHandle> cache = new AtomicReference<>();

  public PricingSnapshotIdentityService(PricingProvider pricingProvider, Clock clock) {
    this.pricingProvider = pricingProvider;
    this.clock = clock;
  }

  /**
   * Returns a {@link PricingSnapshotHandle} computed from the current snapshot list. The handle is
   * cached so successive reads (with no intervening {@link PricingRefreshedEvent}) reuse the same
   * id and capture instant.
   */
  public PricingSnapshotHandle capture() {
    PricingSnapshotHandle cached = cache.get();
    if (cached != null) {
      return cached;
    }
    PricingSnapshotHandle fresh = buildHandle();
    cache.compareAndSet(null, fresh);
    return cache.get();
  }

  /**
   * Computes the {@link PricingSnapshotId} for an arbitrary snapshot list. Exposed as a static
   * helper so tests can assert determinism without depending on Spring.
   */
  public static PricingSnapshotId computeId(List<PricingSnapshot> snapshots) {
    String canonical = canonicalise(snapshots);
    byte[] digest = sha256(canonical.getBytes(StandardCharsets.UTF_8));
    return new PricingSnapshotId(PricingSnapshotId.CURRENT_PREFIX + toLowerHex(digest));
  }

  /**
   * Computes the primary source layer across {@code snapshots}: {@code OVERRIDE} if any present,
   * else {@code REMOTE} if any present, else {@code FALLBACK}.
   */
  public static PricingSource computePrimarySource(List<PricingSnapshot> snapshots) {
    boolean anyOverride = false;
    boolean anyRemote = false;
    for (PricingSnapshot snapshot : snapshots) {
      switch (snapshot.source()) {
        case OVERRIDE -> anyOverride = true;
        case REMOTE -> anyRemote = true;
        case FALLBACK -> {
          // tracked implicitly as default
        }
        default -> {
          // exhaustive
        }
      }
    }
    if (anyOverride) {
      return PricingSource.OVERRIDE;
    }
    if (anyRemote) {
      return PricingSource.REMOTE;
    }
    return PricingSource.FALLBACK;
  }

  @EventListener
  public void onPricingRefreshed(PricingRefreshedEvent event) {
    cache.set(null);
  }

  private PricingSnapshotHandle buildHandle() {
    List<PricingSnapshot> snapshots = pricingProvider.snapshots();
    // Defensive re-sort: never trust upstream order for canonical input.
    List<PricingSnapshot> sorted = snapshots.stream().sorted(CANONICAL_ORDER).toList();
    PricingSnapshotId id = computeId(sorted);
    PricingSource primarySource = computePrimarySource(sorted);
    return new PricingSnapshotHandle(id, primarySource, clock.instant(), sorted);
  }

  private static String canonicalise(List<PricingSnapshot> snapshots) {
    List<PricingSnapshot> sorted = snapshots.stream().sorted(CANONICAL_ORDER).toList();
    StringBuilder buffer = new StringBuilder(sorted.size() * 80);
    for (PricingSnapshot snapshot : sorted) {
      buffer
          .append(snapshot.provider().configKey())
          .append('\t')
          .append(snapshot.model().toLowerCase(Locale.ROOT).trim())
          .append('\t')
          .append(scaled(snapshot.pricing().inputTokenPricePerMillion()))
          .append('\t')
          .append(scaled(snapshot.pricing().outputTokenPricePerMillion()))
          .append('\t')
          .append(snapshot.source().name())
          .append('\n');
    }
    return buffer.toString();
  }

  private static String scaled(BigDecimal value) {
    return value.setScale(6, RoundingMode.HALF_UP).toPlainString();
  }

  private static byte[] sha256(byte[] input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return digest.digest(input);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the JVM", exception);
    }
  }

  private static String toLowerHex(byte[] bytes) {
    char[] hex = new char[bytes.length * 2];
    for (int i = 0; i < bytes.length; i++) {
      int unsigned = bytes[i] & 0xff;
      hex[i * 2] = Character.forDigit(unsigned >>> 4, 16);
      hex[i * 2 + 1] = Character.forDigit(unsigned & 0x0f, 16);
    }
    return new String(hex);
  }
}
