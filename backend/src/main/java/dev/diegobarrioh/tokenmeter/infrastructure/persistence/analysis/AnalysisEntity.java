package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "analysis")
public class AnalysisEntity {
  @Id private UUID id;

  @Column(name = "repository_url", nullable = false, length = 500)
  private String repositoryUrl;

  @Column(name = "clone_url", nullable = false, length = 500)
  private String cloneUrl;

  @Column(name = "owner_name", nullable = false, length = 120)
  private String owner;

  @Column(name = "repository_name", nullable = false, length = 180)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private AnalysisStatus status;

  @Column(name = "total_files", nullable = false)
  private long totalFiles;

  @Column(name = "total_lines", nullable = false)
  private long totalLines;

  @Column(name = "total_bytes", nullable = false)
  private long totalBytes;

  @Column(name = "token_encoding", nullable = false, length = 80)
  private String tokenEncoding;

  @Column(name = "total_tokens", nullable = false)
  private long totalTokens;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<LanguageStatsEntity> languages = new ArrayList<>();

  @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<CostEstimateEntity> costEstimates = new ArrayList<>();

  protected AnalysisEntity() {}

  public AnalysisEntity(
      UUID id,
      String repositoryUrl,
      String cloneUrl,
      String owner,
      String name,
      AnalysisStatus status,
      long totalFiles,
      long totalLines,
      long totalBytes,
      String tokenEncoding,
      long totalTokens,
      Instant createdAt) {
    this.id = id;
    this.repositoryUrl = repositoryUrl;
    this.cloneUrl = cloneUrl;
    this.owner = owner;
    this.name = name;
    this.status = status;
    this.totalFiles = totalFiles;
    this.totalLines = totalLines;
    this.totalBytes = totalBytes;
    this.tokenEncoding = tokenEncoding;
    this.totalTokens = totalTokens;
    this.createdAt = createdAt;
  }

  public void addLanguage(LanguageStatsEntity language) {
    languages.add(language);
    language.setAnalysis(this);
  }

  public void addCostEstimate(CostEstimateEntity costEstimate) {
    costEstimates.add(costEstimate);
    costEstimate.setAnalysis(this);
  }

  public UUID getId() {
    return id;
  }

  public String getRepositoryUrl() {
    return repositoryUrl;
  }

  public String getCloneUrl() {
    return cloneUrl;
  }

  public String getOwner() {
    return owner;
  }

  public String getName() {
    return name;
  }

  public long getTotalFiles() {
    return totalFiles;
  }

  public long getTotalLines() {
    return totalLines;
  }

  public long getTotalBytes() {
    return totalBytes;
  }

  public String getTokenEncoding() {
    return tokenEncoding;
  }

  public long getTotalTokens() {
    return totalTokens;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<LanguageStatsEntity> getLanguages() {
    return List.copyOf(languages);
  }

  public List<CostEstimateEntity> getCostEstimates() {
    return List.copyOf(costEstimates);
  }
}
