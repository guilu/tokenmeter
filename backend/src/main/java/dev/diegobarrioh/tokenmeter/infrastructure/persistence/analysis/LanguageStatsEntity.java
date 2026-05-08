package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "language_stats")
public class LanguageStatsEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "analysis_id", nullable = false)
  private AnalysisEntity analysis;

  @Column(name = "language_name", nullable = false, length = 120)
  private String language;

  @Column(nullable = false)
  private long files;

  @Column(nullable = false)
  private long lines;

  @Column(nullable = false)
  private long bytes;

  @Column(nullable = false)
  private long tokens;

  protected LanguageStatsEntity() {}

  public LanguageStatsEntity(String language, long files, long lines, long bytes, long tokens) {
    this.language = language;
    this.files = files;
    this.lines = lines;
    this.bytes = bytes;
    this.tokens = tokens;
  }

  void setAnalysis(AnalysisEntity analysis) {
    this.analysis = analysis;
  }

  public String getLanguage() {
    return language;
  }

  public long getFiles() {
    return files;
  }

  public long getLines() {
    return lines;
  }

  public long getBytes() {
    return bytes;
  }

  public long getTokens() {
    return tokens;
  }
}
