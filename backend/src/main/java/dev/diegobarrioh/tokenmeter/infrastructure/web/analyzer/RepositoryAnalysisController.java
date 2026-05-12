package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisResult;
import dev.diegobarrioh.tokenmeter.application.analyzer.RepositoryAnalysisService;
import dev.diegobarrioh.tokenmeter.domain.cost.CostEstimationMode;
import dev.diegobarrioh.tokenmeter.domain.cost.ModelCostEstimate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RepositoryAnalysisController {
  private static final NumberFormat CURRENCY_FORMATTER =
      NumberFormat.getCurrencyInstance(Locale.US);
  private static final NumberFormat INTEGER_FORMATTER = NumberFormat.getIntegerInstance(Locale.US);

  private final RepositoryAnalysisService analysisService;
  private final RepositoryAnalysisMapper mapper;
  private final CostBreakdownMapper costBreakdownMapper;
  private final OpenGraphImageRenderer openGraphImageRenderer;
  private final LeaderboardService leaderboardService;

  public RepositoryAnalysisController(
      RepositoryAnalysisService analysisService,
      RepositoryAnalysisMapper mapper,
      CostBreakdownMapper costBreakdownMapper,
      OpenGraphImageRenderer openGraphImageRenderer,
      LeaderboardService leaderboardService) {
    this.analysisService = analysisService;
    this.mapper = mapper;
    this.costBreakdownMapper = costBreakdownMapper;
    this.openGraphImageRenderer = openGraphImageRenderer;
    this.leaderboardService = leaderboardService;
  }

  @PostMapping("/api/analyze")
  @ResponseStatus(HttpStatus.OK)
  public RepositoryAnalysisResponse analyze(@Valid @RequestBody RepositoryAnalysisRequest request) {
    return mapper.toResponse(analysisService.analyze(request.repositoryUrl()));
  }

  @GetMapping("/api/analyze/{id}")
  public RepositoryAnalysisResponse findById(@PathVariable UUID id) {
    return mapper.toResponse(analysisService.findById(id));
  }

  @GetMapping("/api/analyze/{id}/cost-breakdown")
  public CostBreakdownResponse getCostBreakdown(@PathVariable UUID id) {
    return costBreakdownMapper.toResponse(analysisService.findById(id));
  }

  @GetMapping("/api/leaderboards")
  public LeaderboardPageResponse getLeaderboard(
      @RequestParam(defaultValue = "most-expensive") String category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "12") int size,
      @RequestParam(required = false) String mode,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String model) {
    return leaderboardService.getLeaderboard(
        LeaderboardCategory.fromSlug(category), page, size, mode, provider, model);
  }

  @GetMapping(value = "/leaderboards", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> getPublicLeaderboardsPage(HttpServletRequest request) {
    String origin = publicOrigin(request);
    String publicPath = origin + "/leaderboards";
    String title = "TokenMeter repository leaderboards";
    String description =
        "Explore public AI generation cost rankings for analyzed GitHub repositories.";

    String html =
        """
        <!doctype html>
        <html lang=\"en\">
          <head>
            <meta charset=\"UTF-8\" />
            <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
            <meta name=\"description\" content=\"%s\" />
            <meta property=\"og:title\" content=\"%s\" />
            <meta property=\"og:description\" content=\"%s\" />
            <meta property=\"og:type\" content=\"website\" />
            <meta property=\"og:url\" content=\"%s\" />
            <meta name=\"twitter:card\" content=\"summary\" />
            <meta name=\"twitter:title\" content=\"%s\" />
            <meta name=\"twitter:description\" content=\"%s\" />
            <title>%s</title>
            <script>window.location.replace('/?leaderboards=true')</script>
          </head>
          <body>
            <h1>TokenMeter repository leaderboards</h1>
            <p>Loading public rankings…</p>
          </body>
        </html>
        """
            .formatted(
                html(description),
                html(title),
                html(description),
                html(publicPath),
                html(title),
                html(description),
                html(title));

    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
        .body(html);
  }

  @GetMapping(value = "/analysis/{id}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> getPublicAnalysisPage(
      @PathVariable UUID id, HttpServletRequest request) {
    RepositoryAnalysisResult analysis = analysisService.findById(id);
    String origin = publicOrigin(request);
    String publicPath = origin + "/analysis/" + id;
    String imagePath = origin + "/api/analyze/" + id + "/og-image.png?mode=raw&v=range";
    String title = "TokenMeter analysis for " + analysis.owner() + "/" + analysis.name();
    String description = openGraphDescription(analysis);

    String html =
        """
        <!doctype html>
        <html lang=\"en\">
          <head>
            <meta charset=\"UTF-8\" />
            <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />
            <meta name=\"description\" content=\"%s\" />
            <meta property=\"og:title\" content=\"%s\" />
            <meta property=\"og:description\" content=\"%s\" />
            <meta property=\"og:type\" content=\"website\" />
            <meta property=\"og:url\" content=\"%s\" />
            <meta property=\"og:image\" content=\"%s\" />
            <meta property=\"og:image:secure_url\" content=\"%s\" />
            <meta property=\"og:image:type\" content=\"image/png\" />
            <meta property=\"og:image:width\" content=\"1200\" />
            <meta property=\"og:image:height\" content=\"630\" />
            <meta name=\"twitter:card\" content=\"summary_large_image\" />
            <meta name=\"twitter:title\" content=\"%s\" />
            <meta name=\"twitter:description\" content=\"%s\" />
            <meta name=\"twitter:image\" content=\"%s\" />
            <title>%s</title>
            <script>window.location.replace('/?analysis=%s')</script>
          </head>
          <body>
            <p>Loading TokenMeter analysis…</p>
          </body>
        </html>
        """
            .formatted(
                html(description),
                html(title),
                html(description),
                html(publicPath),
                html(imagePath),
                html(imagePath),
                html(title),
                html(description),
                html(imagePath),
                html(title),
                id);

    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
        .body(html);
  }

  @GetMapping(value = "/api/analyze/{id}/og-image.png", produces = MediaType.IMAGE_PNG_VALUE)
  public ResponseEntity<byte[]> getOpenGraphImage(
      @PathVariable UUID id, @RequestParam(required = false) String mode) {
    RepositoryAnalysisResult analysis = analysisService.findById(id);
    byte[] image = openGraphImageRenderer.render(analysis, parseMode(mode));

    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .cacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=tokenmeter-" + id + "-og.png")
        .body(image);
  }

  private static String publicOrigin(HttpServletRequest request) {
    String protocol =
        Optional.ofNullable(request.getHeader("X-Forwarded-Proto")).orElse(request.getScheme());
    String host =
        Optional.ofNullable(request.getHeader("X-Forwarded-Host"))
            .orElse(request.getHeader("Host"));
    if (host == null || host.isBlank()) {
      host = request.getServerName() + ":" + request.getServerPort();
    }
    return protocol + "://" + host;
  }

  private static String openGraphDescription(RepositoryAnalysisResult analysis) {
    Optional<CostRange> range = selectedRawRange(analysis);
    String tokenSummary =
        INTEGER_FORMATTER.format(analysis.tokenization().totalTokens())
            + " tokens, "
            + INTEGER_FORMATTER.format(analysis.scan().totalFiles())
            + " files and "
            + INTEGER_FORMATTER.format(analysis.scan().languages().size())
            + " languages analyzed.";

    return range
        .map(
            value ->
                tokenSummary
                    + " This repository would cost "
                    + costRange(value)
                    + " to generate across supported models.")
        .orElse(tokenSummary + " AI generation cost benchmark.");
  }

  private static Optional<CostRange> selectedRawRange(RepositoryAnalysisResult analysis) {
    Comparator<ModelCostEstimate> byCost = Comparator.comparing(ModelCostEstimate::totalCost);
    var rawEstimates =
        analysis.costEstimates().stream()
            .filter(candidate -> candidate.mode() == CostEstimationMode.RAW)
            .toList();
    if (!rawEstimates.isEmpty()) {
      return Optional.of(
          new CostRange(
              rawEstimates.stream().min(byCost).orElseThrow(),
              rawEstimates.stream().max(byCost).orElseThrow()));
    }
    if (analysis.costEstimates().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new CostRange(
            analysis.costEstimates().stream().min(byCost).orElseThrow(),
            analysis.costEstimates().stream().max(byCost).orElseThrow()));
  }

  private static String costRange(CostRange range) {
    String lowest = CURRENCY_FORMATTER.format(range.lowest().totalCost());
    String highest = CURRENCY_FORMATTER.format(range.highest().totalCost());
    return lowest.equals(highest) ? lowest : lowest + "–" + highest;
  }

  private static String html(String value) {
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }

  private record CostRange(ModelCostEstimate lowest, ModelCostEstimate highest) {}

  private static Optional<CostEstimationMode> parseMode(String mode) {
    if (mode == null || mode.isBlank()) {
      return Optional.empty();
    }

    try {
      return Optional.of(CostEstimationMode.valueOf(mode.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
