package dev.diegobarrioh.tokenmeter.infrastructure.web;

import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardJpaRepository;
import dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.LeaderboardRow;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SitemapController {

  private static final int SITEMAP_ANALYSIS_LIMIT = 200;
  private static final DateTimeFormatter W3C_DATE =
      DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  private final PublicOriginProperties publicOriginProperties;
  private final LeaderboardJpaRepository leaderboardJpaRepository;

  public SitemapController(
      PublicOriginProperties publicOriginProperties,
      LeaderboardJpaRepository leaderboardJpaRepository) {
    this.publicOriginProperties = publicOriginProperties;
    this.leaderboardJpaRepository = leaderboardJpaRepository;
  }

  @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
  public ResponseEntity<String> getSitemap() {
    String origin = publicOriginProperties.publicOrigin();

    List<LeaderboardRow> analyses =
        leaderboardJpaRepository.findMostExpensive(null, null, null, SITEMAP_ANALYSIS_LIMIT, 0);

    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    appendStaticUrl(xml, origin + "/", "<changefreq>daily</changefreq>");
    appendStaticUrl(xml, origin + "/models", null);
    appendStaticUrl(xml, origin + "/leaderboards", null);

    for (LeaderboardRow row : analyses) {
      xml.append("  <url>\n");
      xml.append("    <loc>")
          .append(escapeXml(origin + "/analysis/" + row.getId()))
          .append("</loc>\n");
      xml.append("    <lastmod>")
          .append(W3C_DATE.format(row.getCreatedAt()))
          .append("</lastmod>\n");
      xml.append("  </url>\n");
    }

    xml.append("</urlset>");

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/xml;charset=UTF-8"))
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
        .body(xml.toString());
  }

  @GetMapping(value = "/robots.txt", produces = "text/plain;charset=UTF-8")
  public ResponseEntity<String> getRobotsTxt() {
    String origin = publicOriginProperties.publicOrigin();
    String body = "User-agent: *\n" + "Allow: /\n" + "Sitemap: " + origin + "/sitemap.xml\n";

    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/plain;charset=UTF-8"))
        .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
        .body(body);
  }

  private static void appendStaticUrl(StringBuilder xml, String loc, String extra) {
    xml.append("  <url>\n");
    xml.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
    if (extra != null) {
      xml.append("    ").append(extra).append("\n");
    }
    xml.append("  </url>\n");
  }

  private static String escapeXml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
