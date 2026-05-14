package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class BadgeRenderer {

  private static final int PADDING = 10;
  private static final String LABEL = "AI gen cost";

  public String render(RepositoryAnalysisResult analysis) {
    List<ModelCostEstimate> estimates = rawEstimates(analysis);
    if (estimates.isEmpty()) {
      estimates = analysis.costEstimates();
    }
    if (estimates.isEmpty()) {
      return buildSvg(LABEL, "no data", "#9f9f9f");
    }
    Comparator<ModelCostEstimate> byCost = Comparator.comparing(ModelCostEstimate::totalCost);
    BigDecimal min = estimates.stream().min(byCost).orElseThrow().totalCost();
    BigDecimal max = estimates.stream().max(byCost).orElseThrow().totalCost();
    String value = formatRange(min, max);
    String color = badgeColor(max);
    return buildSvg(LABEL, value, color);
  }

  private static List<ModelCostEstimate> rawEstimates(RepositoryAnalysisResult analysis) {
    return analysis.costEstimates().stream()
        .filter(e -> e.mode() == CostEstimationMode.RAW)
        .toList();
  }

  private static String formatRange(BigDecimal min, BigDecimal max) {
    if (min.compareTo(max) == 0) return formatCost(min);
    return formatCost(min) + " – " + formatCost(max);
  }

  private static String formatCost(BigDecimal cost) {
    if (cost.compareTo(new BigDecimal("0.01")) < 0) return "< $0.01";
    return NumberFormat.getCurrencyInstance(Locale.US).format(cost);
  }

  private static String badgeColor(BigDecimal max) {
    if (max.compareTo(BigDecimal.ONE) < 0) return "#4c1";
    if (max.compareTo(BigDecimal.TEN) < 0) return "#dfb317";
    return "#e05d44";
  }

  private static String buildSvg(String label, String value, String valueColor) {
    int lw = estimateSectionWidth(label);
    int vw = estimateSectionWidth(value);
    int tw = lw + vw;
    // SVG uses scale(.1) with font-size 110 → coordinates in 10× pixel space
    int lxCenter = lw * 5;
    int vxCenter = (lw + vw / 2) * 10;
    int lTextLen = (lw - 2 * PADDING) * 10;
    int vTextLen = (vw - 2 * PADDING) * 10;
    String sl = xmlEscape(label);
    String sv = xmlEscape(value);
    return ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\""
        + tw
        + "\" height=\"20\""
        + " role=\"img\" aria-label=\""
        + sl
        + ": "
        + sv
        + "\">\n"
        + "  <title>"
        + sl
        + ": "
        + sv
        + "</title>\n"
        + "  <linearGradient id=\"s\" x2=\"0\" y2=\"100%\">\n"
        + "    <stop offset=\"0\" stop-color=\"#bbb\" stop-opacity=\".1\"/>\n"
        + "    <stop offset=\"1\" stop-opacity=\".1\"/>\n"
        + "  </linearGradient>\n"
        + "  <clipPath id=\"r\">\n"
        + "    <rect width=\""
        + tw
        + "\" height=\"20\" rx=\"3\" fill=\"#fff\"/>\n"
        + "  </clipPath>\n"
        + "  <g clip-path=\"url(#r)\">\n"
        + "    <rect width=\""
        + lw
        + "\" height=\"20\" fill=\"#555\"/>\n"
        + "    <rect x=\""
        + lw
        + "\" width=\""
        + vw
        + "\" height=\"20\" fill=\""
        + valueColor
        + "\"/>\n"
        + "    <rect width=\""
        + tw
        + "\" height=\"20\" fill=\"url(#s)\"/>\n"
        + "  </g>\n"
        + "  <g fill=\"#fff\" text-anchor=\"middle\""
        + " font-family=\"DejaVu Sans,Verdana,Geneva,sans-serif\" font-size=\"110\">\n"
        + "    <text aria-hidden=\"true\" x=\""
        + lxCenter
        + "\" y=\"150\""
        + " fill=\"#010101\" fill-opacity=\".3\" transform=\"scale(.1)\""
        + " textLength=\""
        + lTextLen
        + "\" lengthAdjust=\"spacing\">"
        + sl
        + "</text>\n"
        + "    <text x=\""
        + lxCenter
        + "\" y=\"140\" transform=\"scale(.1)\""
        + " textLength=\""
        + lTextLen
        + "\" lengthAdjust=\"spacing\">"
        + sl
        + "</text>\n"
        + "    <text aria-hidden=\"true\" x=\""
        + vxCenter
        + "\" y=\"150\""
        + " fill=\"#010101\" fill-opacity=\".3\" transform=\"scale(.1)\""
        + " textLength=\""
        + vTextLen
        + "\" lengthAdjust=\"spacing\">"
        + sv
        + "</text>\n"
        + "    <text x=\""
        + vxCenter
        + "\" y=\"140\" transform=\"scale(.1)\""
        + " textLength=\""
        + vTextLen
        + "\" lengthAdjust=\"spacing\">"
        + sv
        + "</text>\n"
        + "  </g>\n"
        + "</svg>");
  }

  static int estimateSectionWidth(String text) {
    return Math.max(charWidthSum(text) + 2 * PADDING, 30);
  }

  static int charWidthSum(String text) {
    int total = 0;
    for (int i = 0; i < text.length(); i++) {
      total += charWidth(text.charAt(i));
    }
    return total;
  }

  private static int charWidth(char c) {
    return switch (c) {
      case 'f', 'i', 'j', 'l', 'r', 't' -> 4;
      case 'I', '1', '|', '!' -> 3;
      case 'm', 'w' -> 9;
      case 'M', 'W' -> 10;
      case ' ' -> 4;
      case '.', ',', ':' -> 3;
      case '$' -> 7;
      case '–', '—', '-' -> 5;
      case '<' -> 5;
      default -> 6;
    };
  }

  private static String xmlEscape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
