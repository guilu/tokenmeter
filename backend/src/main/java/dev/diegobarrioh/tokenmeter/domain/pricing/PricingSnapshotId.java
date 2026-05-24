package dev.diegobarrioh.tokenmeter.domain.pricing;

import java.util.regex.Pattern;

/**
 * Deterministic, content-derived identifier of an active {@link PricingSnapshot} list.
 *
 * <p>The canonical form is {@code v1:} followed by 64 lowercase hexadecimal characters (the SHA-256
 * digest of the canonical serialisation of the active snapshot list). The {@code v1:} prefix is
 * stored inline to keep the identifier self-describing and future-proof: a future canonicalisation
 * change MUST introduce a new prefix (e.g. {@code v2:}) rather than overwrite the existing one.
 *
 * <p>Total length is exactly 67 chars; the column reserves 80 to leave headroom for a future
 * prefix bump.
 */
public record PricingSnapshotId(String value) {

  /** Current canonical version prefix. */
  public static final String CURRENT_PREFIX = "v1:";

  /** Total expected length: {@code v1:} (3) + 64 hex chars. */
  public static final int EXPECTED_LENGTH = CURRENT_PREFIX.length() + 64;

  private static final Pattern V1_PATTERN = Pattern.compile("^v1:[0-9a-f]{64}$");

  public PricingSnapshotId {
    if (value == null) {
      throw new IllegalArgumentException("pricing snapshot id must not be null");
    }
    if (value.length() != EXPECTED_LENGTH) {
      throw new IllegalArgumentException(
          "pricing snapshot id must be exactly "
              + EXPECTED_LENGTH
              + " chars (was "
              + value.length()
              + "): "
              + value);
    }
    if (!V1_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "pricing snapshot id must match 'v1:<64-hex>' (was: " + value + ")");
    }
  }

  @Override
  public String toString() {
    return value;
  }
}
