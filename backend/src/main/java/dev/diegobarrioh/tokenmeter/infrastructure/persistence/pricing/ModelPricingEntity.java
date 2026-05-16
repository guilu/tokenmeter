package dev.diegobarrioh.tokenmeter.infrastructure.persistence.pricing;

import dev.diegobarrioh.tokenmeter.domain.pricing.PricingSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JPA representation of a row in the {@code model_pricing} table. The mapping between this entity
 * and {@link dev.diegobarrioh.tokenmeter.domain.pricing.PricingSnapshot} lives in {@code
 * JpaPricingSnapshotStore} so the entity stays free of domain orchestration.
 */
@Entity
@Table(
    name = "model_pricing",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_model_pricing_provider_model",
            columnNames = {"provider", "model"}))
public class ModelPricingEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String provider;

  @Column(nullable = false, length = 128)
  private String model;

  @Column(name = "input_price_per_million", nullable = false, precision = 12, scale = 6)
  private BigDecimal inputPricePerMillion;

  @Column(name = "output_price_per_million", nullable = false, precision = 12, scale = 6)
  private BigDecimal outputPricePerMillion;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PricingSource source;

  @Column(name = "fetched_at", nullable = false)
  private OffsetDateTime fetchedAt;

  @Column(name = "external_model_id", length = 255)
  private String externalModelId;

  @Column(name = "deprecated_at")
  private OffsetDateTime deprecatedAt;

  protected ModelPricingEntity() {}

  public ModelPricingEntity(
      String provider,
      String model,
      BigDecimal inputPricePerMillion,
      BigDecimal outputPricePerMillion,
      PricingSource source,
      OffsetDateTime fetchedAt,
      String externalModelId,
      OffsetDateTime deprecatedAt) {
    this.provider = provider;
    this.model = model;
    this.inputPricePerMillion = inputPricePerMillion;
    this.outputPricePerMillion = outputPricePerMillion;
    this.source = source;
    this.fetchedAt = fetchedAt;
    this.externalModelId = externalModelId;
    this.deprecatedAt = deprecatedAt;
  }

  public Long getId() {
    return id;
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public BigDecimal getInputPricePerMillion() {
    return inputPricePerMillion;
  }

  public BigDecimal getOutputPricePerMillion() {
    return outputPricePerMillion;
  }

  public PricingSource getSource() {
    return source;
  }

  public OffsetDateTime getFetchedAt() {
    return fetchedAt;
  }

  public String getExternalModelId() {
    return externalModelId;
  }

  public OffsetDateTime getDeprecatedAt() {
    return deprecatedAt;
  }
}
