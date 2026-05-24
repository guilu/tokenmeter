package dev.diegobarrioh.tokenmeter.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PricingSnapshotIdTest {

  private static final String VALID_HEX = "0".repeat(64);

  @Test
  void acceptsCanonicalV1Identifier() {
    PricingSnapshotId id = new PricingSnapshotId("v1:" + VALID_HEX);

    assertThat(id.value()).startsWith("v1:");
    assertThat(id.value()).hasSize(PricingSnapshotId.EXPECTED_LENGTH);
    assertThat(id.toString()).isEqualTo("v1:" + VALID_HEX);
  }

  @Test
  void rejectsNullValue() {
    assertThatThrownBy(() -> new PricingSnapshotId(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be null");
  }

  @Test
  void rejectsValueWithoutV1Prefix() {
    assertThatThrownBy(() -> new PricingSnapshotId("v2:" + VALID_HEX))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("v1:");
  }

  @Test
  void rejectsValueWithWrongLength() {
    assertThatThrownBy(() -> new PricingSnapshotId("v1:" + "0".repeat(63)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly");
  }

  @Test
  void rejectsValueWithUppercaseHex() {
    assertThatThrownBy(() -> new PricingSnapshotId("v1:" + "A".repeat(64)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsValueWithNonHexCharacters() {
    assertThatThrownBy(() -> new PricingSnapshotId("v1:" + "z".repeat(64)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
