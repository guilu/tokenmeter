package dev.diegobarrioh.tokenmeter.infrastructure.web.analyzer;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboards/insights")
public class LeaderboardInsightsController {

  private final LeaderboardInsightsService insightsService;

  public LeaderboardInsightsController(LeaderboardInsightsService insightsService) {
    this.insightsService = insightsService;
  }

  @GetMapping("/overview")
  public ResponseEntity<LeaderboardOverviewResponse> getOverview(
      @RequestParam(required = false) String mode,
      @RequestParam(required = false) String provider,
      @RequestParam(required = false) String model) {
    LeaderboardOverviewResponse body = insightsService.getOverview(mode, provider, model);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
        .body(body);
  }

  @GetMapping("/languages")
  public ResponseEntity<LeaderboardLanguagesResponse> getLanguages() {
    LeaderboardLanguagesResponse body = insightsService.getLanguages();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofSeconds(300)).cachePublic())
        .body(body);
  }
}
