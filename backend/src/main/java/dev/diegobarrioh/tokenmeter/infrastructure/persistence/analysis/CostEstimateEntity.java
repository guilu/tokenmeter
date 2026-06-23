package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.pricing.AiProvider;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenizationPrecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "cost_estimates")
public class CostEstimateEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "analysis_id", nullable = false)
  private AnalysisEntity analysis;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 60)
  private AiProvider provider;

  @Column(nullable = false, length = 180)
  private String model;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private CostEstimationMode mode;

  @Column(name = "base_tokens", nullable = false)
  private long baseTokens;

  @Column(name = "estimated_input_tokens", nullable = false)
  private long estimatedInputTokens;

  @Column(name = "estimated_output_tokens", nullable = false)
  private long estimatedOutputTokens;

  @Column(name = "input_cost", nullable = false, precision = 20, scale = 6)
  private BigDecimal inputCost;

  @Column(name = "output_cost", nullable = false, precision = 20, scale = 6)
  private BigDecimal outputCost;

  @Column(name = "total_cost", nullable = false, precision = 20, scale = 6)
  private BigDecimal totalCost;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String formula;

  @Column(name = "tokenizer_id", length = 120)
  private String tokenizerId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tokenization_precision", length = 30)
  private TokenizationPrecision tokenizationPrecision;

  protected CostEstimateEntity() {}

  public CostEstimateEntity(
      AiProvider provider,
      String model,
      CostEstimationMode mode,
      long baseTokens,
      long estimatedInputTokens,
      long estimatedOutputTokens,
      BigDecimal inputCost,
      BigDecimal outputCost,
      BigDecimal totalCost,
      String formula,
      String tokenizerId,
      TokenizationPrecision tokenizationPrecision) {
    this.provider = provider;
    this.model = model;
    this.mode = mode;
    this.baseTokens = baseTokens;
    this.estimatedInputTokens = estimatedInputTokens;
    this.estimatedOutputTokens = estimatedOutputTokens;
    this.inputCost = inputCost;
    this.outputCost = outputCost;
    this.totalCost = totalCost;
    this.formula = formula;
    this.tokenizerId = tokenizerId;
    this.tokenizationPrecision = tokenizationPrecision;
  }

  void setAnalysis(AnalysisEntity analysis) {
    this.analysis = analysis;
  }

  public AiProvider getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public CostEstimationMode getMode() {
    return mode;
  }

  public long getBaseTokens() {
    return baseTokens;
  }

  public long getEstimatedInputTokens() {
    return estimatedInputTokens;
  }

  public long getEstimatedOutputTokens() {
    return estimatedOutputTokens;
  }

  public BigDecimal getInputCost() {
    return inputCost;
  }

  public BigDecimal getOutputCost() {
    return outputCost;
  }

  public BigDecimal getTotalCost() {
    return totalCost;
  }

  public String getFormula() {
    return formula;
  }

  public String getTokenizerId() {
    return tokenizerId;
  }

  public TokenizationPrecision getTokenizationPrecision() {
    return tokenizationPrecision;
  }
}
