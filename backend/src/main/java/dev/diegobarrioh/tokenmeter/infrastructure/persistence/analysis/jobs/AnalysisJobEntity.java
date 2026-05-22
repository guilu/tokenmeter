package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.jobs;

import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobPhase;
import dev.diegobarrioh.tokenmeter.domain.job.AnalysisJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_job")
public class AnalysisJobEntity {

  @Id private UUID id;

  @Column(name = "repository_url", nullable = false, length = 500)
  private String repositoryUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AnalysisJobStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AnalysisJobPhase phase;

  @Column(name = "progress_percent", nullable = false)
  private short progressPercent;

  @Column(name = "message")
  private String message;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "files_discovered")
  private Long filesDiscovered;

  @Column(name = "files_processed")
  private Long filesProcessed;

  @Column(name = "files_skipped")
  private Long filesSkipped;

  @Column(name = "tokens_counted")
  private Long tokensCounted;

  @Column(name = "context_windows")
  private Integer contextWindows;

  @Column(name = "pricing_models_processed")
  private Integer pricingModelsProcessed;

  @Column(name = "analysis_id")
  private UUID analysisId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected AnalysisJobEntity() {}

  public AnalysisJobEntity(
      UUID id,
      String repositoryUrl,
      AnalysisJobStatus status,
      AnalysisJobPhase phase,
      short progressPercent,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.repositoryUrl = repositoryUrl;
    this.status = status;
    this.phase = phase;
    this.progressPercent = progressPercent;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getRepositoryUrl() {
    return repositoryUrl;
  }

  public AnalysisJobStatus getStatus() {
    return status;
  }

  public void setStatus(AnalysisJobStatus status) {
    this.status = status;
  }

  public AnalysisJobPhase getPhase() {
    return phase;
  }

  public void setPhase(AnalysisJobPhase phase) {
    this.phase = phase;
  }

  public short getProgressPercent() {
    return progressPercent;
  }

  public void setProgressPercent(short progressPercent) {
    this.progressPercent = progressPercent;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Long getFilesDiscovered() {
    return filesDiscovered;
  }

  public void setFilesDiscovered(Long filesDiscovered) {
    this.filesDiscovered = filesDiscovered;
  }

  public Long getFilesProcessed() {
    return filesProcessed;
  }

  public void setFilesProcessed(Long filesProcessed) {
    this.filesProcessed = filesProcessed;
  }

  public Long getFilesSkipped() {
    return filesSkipped;
  }

  public void setFilesSkipped(Long filesSkipped) {
    this.filesSkipped = filesSkipped;
  }

  public Long getTokensCounted() {
    return tokensCounted;
  }

  public void setTokensCounted(Long tokensCounted) {
    this.tokensCounted = tokensCounted;
  }

  public Integer getContextWindows() {
    return contextWindows;
  }

  public void setContextWindows(Integer contextWindows) {
    this.contextWindows = contextWindows;
  }

  public Integer getPricingModelsProcessed() {
    return pricingModelsProcessed;
  }

  public void setPricingModelsProcessed(Integer pricingModelsProcessed) {
    this.pricingModelsProcessed = pricingModelsProcessed;
  }

  public UUID getAnalysisId() {
    return analysisId;
  }

  public void setAnalysisId(UUID analysisId) {
    this.analysisId = analysisId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }
}
