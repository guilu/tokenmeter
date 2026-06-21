package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisJpaRepository extends JpaRepository<AnalysisEntity, UUID> {
  @Override
  @EntityGraph(attributePaths = "languages")
  java.util.Optional<AnalysisEntity> findById(UUID id);

  @Query(
      "SELECT a.id FROM AnalysisEntity a"
          + " WHERE a.repositoryUrl = :repositoryUrl"
          + " AND a.pricingSnapshotId = :pricingSnapshotId"
          + " AND a.status = dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.AnalysisStatus.SUCCESS"
          + " ORDER BY a.createdAt DESC")
  List<UUID> findLatestSuccessId(
      @Param("repositoryUrl") String repositoryUrl,
      @Param("pricingSnapshotId") String pricingSnapshotId,
      Pageable pageable);

  @Query(
      "SELECT DISTINCT a.repositoryUrl FROM AnalysisEntity a"
          + " WHERE a.repositoryUrl IN :repositoryUrls"
          + " AND a.status = dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis.AnalysisStatus.SUCCESS")
  List<String> findAnalyzedRepositoryUrls(
      @Param("repositoryUrls") Collection<String> repositoryUrls);
}
