package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.LanguageTokenMetrics;
import java.text.NumberFormat;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Renders a completed {@link RepositoryAnalysisResult} as a Markdown document for download.
 *
 * <p>This is a pure infrastructure view component — no business logic is introduced here. All
 * values are taken from the already-computed public read model. This mirrors the structural pattern
 * of {@link BadgeRenderer} and {@link OpenGraphImageRenderer}.
 *
 * <p>IMPORTANT: The output MUST NOT include any filesystem paths, job identifiers, queue state, or
 * any other server internals. Only public read-model fields are permitted.
 */
@Component
public class MarkdownExportRenderer {

  private static final NumberFormat INTEGER_FORMATTER = NumberFormat.getIntegerInstance(Locale.US);
  private static final DateTimeFormatter ISO_UTC =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  /**
   * Renders the analysis as a Markdown string suitable for file download.
   *
   * @param analysis the completed analysis result (public read model)
   * @return a Markdown document string
   */
  public String render(RepositoryAnalysisResult analysis) {
    StringBuilder sb = new StringBuilder();

    appendHeader(sb, analysis);
    appendMetadata(sb, analysis);
    appendTotals(sb, analysis);
    appendLanguageBreakdown(sb, analysis);
    appendCostEstimates(sb, analysis);
    appendPricingFooter(sb, analysis);
    appendDisclaimer(sb);

    return sb.toString();
  }

  /**
   * Returns the suggested download filename for the given analysis.
   *
   * <p>Format: {@code tokenmeter-{owner}-{name}.md} with slug rules applied (lowercase, non-{@code
   * [a-z0-9-]} replaced with {@code -}, consecutive dashes collapsed, leading/trailing dashes
   * trimmed). Falls back to {@code tokenmeter-analysis-{id}.md} when owner or name is blank.
   *
   * @param analysis the analysis
   * @return the suggested filename
   */
  public String filename(RepositoryAnalysisResult analysis) {
    String owner = analysis.owner();
    String name = analysis.name();
    if (owner == null || owner.isBlank() || name == null || name.isBlank()) {
      return "tokenmeter-analysis-" + analysis.id() + ".md";
    }
    return "tokenmeter-" + slug(owner) + "-" + slug(name) + ".md";
  }

  // ---------------------------------------------------------------------------
  // Section renderers
  // ---------------------------------------------------------------------------

  private static void appendHeader(StringBuilder sb, RepositoryAnalysisResult analysis) {
    String repoDisplay = analysis.owner() + "/" + analysis.name();
    sb.append("# TokenMeter cost report — ")
        .append(repoDisplay)
        .append("\n\n")
        .append("[")
        .append(analysis.repositoryUrl())
        .append("](")
        .append(analysis.repositoryUrl())
        .append(")\n\n");
  }

  private static void appendMetadata(StringBuilder sb, RepositoryAnalysisResult analysis) {
    sb.append("## Metadata\n\n");
    sb.append("| Field | Value |\n");
    sb.append("|---|---|\n");
    sb.append("| Analysis id | ").append(analysis.id()).append(" |\n");
    String createdAt =
        analysis.createdAt() != null ? ISO_UTC.format(analysis.createdAt()) : "unknown";
    sb.append("| Created | ").append(createdAt).append(" |\n");
    String encoding =
        analysis.tokenization() != null ? analysis.tokenization().encoding() : "unknown";
    sb.append("| Token encoding | ").append(encoding).append(" |\n");
    sb.append("\n");
  }

  private static void appendTotals(StringBuilder sb, RepositoryAnalysisResult analysis) {
    sb.append("## Totals\n\n");
    sb.append("| Metric | Value |\n");
    sb.append("|---|---|\n");
    sb.append("| Files | ")
        .append(INTEGER_FORMATTER.format(analysis.scan().totalFiles()))
        .append(" |\n");
    sb.append("| Lines | ")
        .append(INTEGER_FORMATTER.format(analysis.scan().totalLines()))
        .append(" |\n");
    sb.append("| Bytes | ")
        .append(INTEGER_FORMATTER.format(analysis.scan().totalBytes()))
        .append(" |\n");
    sb.append("| Total tokens | ")
        .append(INTEGER_FORMATTER.format(analysis.tokenization().totalTokens()))
        .append(" |\n");
    sb.append("\n");
  }

  private static void appendLanguageBreakdown(StringBuilder sb, RepositoryAnalysisResult analysis) {
    sb.append("## Language breakdown\n\n");
    Map<String, LanguageTokenMetrics> langMetrics = analysis.tokenization().languages();
    if (langMetrics == null || langMetrics.isEmpty()) {
      sb.append("No language metrics available.\n\n");
      return;
    }
    sb.append("| Language | Files | Tokens |\n");
    sb.append("|---|---|---|\n");
    langMetrics.values().stream()
        .sorted(Comparator.comparingLong(LanguageTokenMetrics::tokens).reversed())
        .forEach(
            lang ->
                sb.append("| ")
                    .append(lang.language())
                    .append(" | ")
                    .append(INTEGER_FORMATTER.format(lang.files()))
                    .append(" | ")
                    .append(INTEGER_FORMATTER.format(lang.tokens()))
                    .append(" |\n"));
    sb.append("\n");
  }

  private static void appendCostEstimates(StringBuilder sb, RepositoryAnalysisResult analysis) {
    sb.append("## Cost estimates\n\n");
    List<ModelCostEstimate> estimates = analysis.costEstimates();
    if (estimates == null || estimates.isEmpty()) {
      sb.append("No cost estimates available.\n\n");
      return;
    }
    // Group by mode, ordered RAW → ASSISTED → AGENTIC
    Map<CostEstimationMode, List<ModelCostEstimate>> byMode =
        estimates.stream().collect(Collectors.groupingBy(ModelCostEstimate::mode));

    Arrays.stream(CostEstimationMode.values())
        .filter(byMode::containsKey)
        .forEach(
            mode -> {
              sb.append("### ").append(mode.name()).append("\n\n");
              sb.append("| Provider | Model | Input $/M | Output $/M | Total $ | Formula |\n");
              sb.append("|---|---|---|---|---|---|\n");
              byMode.get(mode).stream()
                  .sorted(
                      Comparator.comparing((ModelCostEstimate e) -> e.provider().name())
                          .thenComparing(ModelCostEstimate::model))
                  .forEach(
                      estimate ->
                          sb.append("| ")
                              .append(estimate.provider().configKey())
                              .append(" | ")
                              .append(estimate.model())
                              .append(" | $")
                              .append(estimate.inputCost().toPlainString())
                              .append(" | $")
                              .append(estimate.outputCost().toPlainString())
                              .append(" | $")
                              .append(estimate.totalCost().toPlainString())
                              .append(" | ")
                              .append(estimate.formula())
                              .append(" |\n"));
              sb.append("\n");
            });
  }

  private static void appendPricingFooter(StringBuilder sb, RepositoryAnalysisResult analysis) {
    sb.append("## Pricing snapshot\n\n");
    if (analysis.pricing() == null) {
      sb.append("Pricing snapshot: not available\n\n");
      return;
    }
    sb.append("| Field | Value |\n");
    sb.append("|---|---|\n");
    sb.append("| Snapshot id | ").append(analysis.pricing().id().value()).append(" |\n");
    sb.append("| Primary source | ")
        .append(analysis.pricing().primarySource().name())
        .append(" |\n");
    sb.append("| Captured at | ")
        .append(ISO_UTC.format(analysis.pricing().capturedAt()))
        .append(" |\n");
    sb.append("\n");
  }

  private static void appendDisclaimer(StringBuilder sb) {
    sb.append("---\n\n");
    sb.append(
        "> **Disclaimer**: These cost figures represent a *floor estimate* — "
            + "the minimum generation cost based solely on repository token count and "
            + "published per-token pricing. They do not include prompt overhead, "
            + "failed attempts, re-runs, reasoning tokens beyond the defined multipliers, "
            + "or any negotiated/discounted rates.\n");
  }

  // ---------------------------------------------------------------------------
  // Slug helper
  // ---------------------------------------------------------------------------

  private static String slug(String value) {
    String lowered = value.toLowerCase(Locale.ROOT);
    String replaced = lowered.replaceAll("[^a-z0-9-]", "-");
    String collapsed = replaced.replaceAll("-{2,}", "-");
    return collapsed.replaceAll("^-|-$", "");
  }
}
