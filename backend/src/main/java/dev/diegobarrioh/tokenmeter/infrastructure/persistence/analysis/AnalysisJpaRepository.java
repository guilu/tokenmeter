package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJpaRepository extends JpaRepository<AnalysisEntity, UUID> {
  @Override
  @EntityGraph(attributePaths = "languages")
  java.util.Optional<AnalysisEntity> findById(UUID id);

  @Override
  @EntityGraph(attributePaths = "costEstimates")
  List<AnalysisEntity> findAll();
}
