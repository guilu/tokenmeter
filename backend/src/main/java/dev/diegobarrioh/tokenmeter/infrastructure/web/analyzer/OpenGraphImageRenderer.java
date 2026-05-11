package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
public class OpenGraphImageRenderer {
  static final int WIDTH = 1200;
  static final int HEIGHT = 630;

  private static final Color BACKGROUND = new Color(2, 6, 23);
  private static final Color PANEL = new Color(15, 23, 42, 235);
  private static final Color PANEL_BORDER = new Color(148, 163, 184, 45);
  private static final Color CYAN = new Color(103, 232, 249);
  private static final Color EMERALD = new Color(110, 231, 183);
  private static final Color WHITE = new Color(248, 250, 252);
  private static final Color SLATE = new Color(148, 163, 184);
  private static final Color SLATE_LIGHT = new Color(203, 213, 225);

  private static final NumberFormat CURRENCY_FORMATTER =
      NumberFormat.getCurrencyInstance(Locale.US);
  private static final NumberFormat INTEGER_FORMATTER = NumberFormat.getIntegerInstance(Locale.US);

  public byte[] render(
      RepositoryAnalysisResult analysis, Optional<CostEstimationMode> requestedMode) {
    BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();

    try {
      configureGraphics(graphics);
      paintBackground(graphics);
      paintCard(graphics, analysis, selectedRange(analysis, requestedMode));
      return toPng(image);
    } finally {
      graphics.dispose();
    }
  }

  private static Optional<CostRange> selectedRange(
      RepositoryAnalysisResult analysis, Optional<CostEstimationMode> requestedMode) {
    Comparator<ModelCostEstimate> byCost = Comparator.comparing(ModelCostEstimate::totalCost);

    if (requestedMode.isPresent()) {
      List<ModelCostEstimate> estimates =
          analysis.costEstimates().stream()
              .filter(candidate -> candidate.mode() == requestedMode.get())
              .toList();
      if (!estimates.isEmpty()) {
        return Optional.of(
            new CostRange(
                estimates.stream().min(byCost).orElseThrow(),
                estimates.stream().max(byCost).orElseThrow(),
                requestedMode.get()));
      }
    }

    List<ModelCostEstimate> rawEstimates =
        analysis.costEstimates().stream()
            .filter(candidate -> candidate.mode() == CostEstimationMode.RAW)
            .toList();
    if (!rawEstimates.isEmpty()) {
      return Optional.of(
          new CostRange(
              rawEstimates.stream().min(byCost).orElseThrow(),
              rawEstimates.stream().max(byCost).orElseThrow(),
              CostEstimationMode.RAW));
    }

    if (analysis.costEstimates().isEmpty()) {
      return Optional.empty();
    }

    ModelCostEstimate lowest = analysis.costEstimates().stream().min(byCost).orElseThrow();
    ModelCostEstimate highest = analysis.costEstimates().stream().max(byCost).orElseThrow();
    return Optional.of(new CostRange(lowest, highest, lowest.mode()));
  }

  private static void configureGraphics(Graphics2D graphics) {
    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    graphics.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
  }

  private static void paintBackground(Graphics2D graphics) {
    graphics.setColor(BACKGROUND);
    graphics.fillRect(0, 0, WIDTH, HEIGHT);

    graphics.setColor(new Color(8, 145, 178, 95));
    graphics.fillOval(-130, -190, 560, 560);
    graphics.setColor(new Color(16, 185, 129, 72));
    graphics.fillOval(760, 320, 520, 520);
    graphics.setColor(new Color(30, 41, 59, 135));
    graphics.fillOval(410, -170, 620, 420);
  }

  private static void paintCard(
      Graphics2D graphics, RepositoryAnalysisResult analysis, Optional<CostRange> range) {
    graphics.setColor(PANEL);
    graphics.fill(new RoundRectangle2D.Double(54, 48, 1092, 534, 42, 42));
    graphics.setColor(PANEL_BORDER);
    graphics.setStroke(new BasicStroke(2));
    graphics.draw(new RoundRectangle2D.Double(54, 48, 1092, 534, 42, 42));

    paintBrand(graphics);
    paintRepository(graphics, analysis);
    paintEstimate(graphics, range);
    paintMetrics(graphics, analysis, range);
  }

  private static void paintBrand(Graphics2D graphics) {
    graphics.setColor(new Color(34, 211, 238, 34));
    graphics.fill(new RoundRectangle2D.Double(88, 82, 184, 46, 23, 23));
    graphics.setColor(CYAN);
    graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
    graphics.drawString("TokenMeter", 112, 113);

    graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 22));
    graphics.setColor(SLATE);
    graphics.drawString("AI repository cost intelligence", 744, 113);
  }

  private static void paintRepository(Graphics2D graphics, RepositoryAnalysisResult analysis) {
    String repositoryName = analysis.owner() + "/" + analysis.name();

    graphics.setColor(SLATE);
    graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
    graphics.drawString("Public repository analysis", 92, 190);

    graphics.setColor(WHITE);
    graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 58));
    drawFittedText(graphics, repositoryName, 92, 260, 930);
  }

  private static void paintEstimate(Graphics2D graphics, Optional<CostRange> range) {
    String costRange = range.map(OpenGraphImageRenderer::costRange).orElse("N/A");
    String modelRange = range.map(OpenGraphImageRenderer::modelRange).orElse("No pricing models");
    String mode = range.map(value -> titleCase(value.mode().name())).orElse("Pending");
    String summary = "This repository would cost " + costRange + " to generate";

    graphics.setColor(EMERALD);
    graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 48));
    drawWrappedText(graphics, summary, 92, 338, 960, 58, 2);

    graphics.setColor(SLATE_LIGHT);
    graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 26));
    drawFittedText(graphics, "Selected mode: " + mode + "  ·  Models: " + modelRange, 92, 454, 960);
  }

  private static void paintMetrics(
      Graphics2D graphics, RepositoryAnalysisResult analysis, Optional<CostRange> range) {
    int y = 508;
    paintMetric(
        graphics, 92, y, "Tokens", INTEGER_FORMATTER.format(analysis.tokenization().totalTokens()));
    paintMetric(graphics, 326, y, "Files", INTEGER_FORMATTER.format(analysis.scan().totalFiles()));
    paintMetric(
        graphics,
        528,
        y,
        "Languages",
        INTEGER_FORMATTER.format(analysis.scan().languages().size()));
    paintMetric(
        graphics,
        770,
        y,
        "Output token range",
        range.map(OpenGraphImageRenderer::outputTokenRange).orElse("N/A"));
  }

  private static String costRange(CostRange range) {
    String lowest = CURRENCY_FORMATTER.format(range.lowest().totalCost());
    String highest = CURRENCY_FORMATTER.format(range.highest().totalCost());
    return lowest.equals(highest) ? lowest : lowest + "–" + highest;
  }

  private static String modelRange(CostRange range) {
    String lowest = range.lowest().model();
    String highest = range.highest().model();
    return lowest.equals(highest) ? lowest : lowest + " to " + highest;
  }

  private static String outputTokenRange(CostRange range) {
    String lowest = INTEGER_FORMATTER.format(range.lowest().estimatedOutputTokens());
    String highest = INTEGER_FORMATTER.format(range.highest().estimatedOutputTokens());
    return lowest.equals(highest) ? lowest : lowest + "–" + highest;
  }

  private static void paintMetric(Graphics2D graphics, int x, int y, String label, String value) {
    graphics.setColor(SLATE);
    graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
    graphics.drawString(label, x, y);
    graphics.setColor(WHITE);
    graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
    graphics.drawString(value, x, y + 38);
  }

  private static void drawWrappedText(
      Graphics2D graphics, String text, int x, int y, int maxWidth, int lineHeight, int maxLines) {
    FontMetrics metrics = graphics.getFontMetrics();
    StringBuilder line = new StringBuilder();
    int currentY = y;
    int lines = 1;

    for (String word : text.split(" ")) {
      String candidate = line.isEmpty() ? word : line + " " + word;
      if (metrics.stringWidth(candidate) <= maxWidth) {
        line = new StringBuilder(candidate);
        continue;
      }

      String rendered =
          lines == maxLines ? ellipsize(metrics, candidate, maxWidth) : line.toString();
      graphics.drawString(rendered, x, currentY);
      if (lines == maxLines) {
        return;
      }
      line = new StringBuilder(word);
      currentY += lineHeight;
      lines++;
    }

    if (!line.isEmpty()) {
      graphics.drawString(line.toString(), x, currentY);
    }
  }

  private static void drawFittedText(Graphics2D graphics, String text, int x, int y, int maxWidth) {
    FontMetrics metrics = graphics.getFontMetrics();
    graphics.drawString(
        metrics.stringWidth(text) <= maxWidth ? text : ellipsize(metrics, text, maxWidth), x, y);
  }

  private static String ellipsize(FontMetrics metrics, String text, int maxWidth) {
    String suffix = "…";
    String candidate = text;
    while (!candidate.isEmpty() && metrics.stringWidth(candidate + suffix) > maxWidth) {
      candidate = candidate.substring(0, candidate.length() - 1);
    }
    return candidate + suffix;
  }

  private static String titleCase(String value) {
    String lowerCase = value.toLowerCase(Locale.ROOT);
    return lowerCase.substring(0, 1).toUpperCase(Locale.ROOT) + lowerCase.substring(1);
  }

  private static byte[] toPng(BufferedImage image) {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", output);
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Could not render OpenGraph image", exception);
    }
  }

  private record CostRange(
      ModelCostEstimate lowest, ModelCostEstimate highest, CostEstimationMode mode) {}
}
