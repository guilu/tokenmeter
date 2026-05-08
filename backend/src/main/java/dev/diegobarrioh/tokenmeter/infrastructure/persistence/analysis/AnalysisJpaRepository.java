package dev.diegobarrioh.tokenmeter.infrastructure.persistence.analysis;

import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisJpaRepository extends JpaRepository<AnalysisEntity, UUID> {
  @Override
  @EntityGraph(attributePaths = "languages")
  java.util.Optional<AnalysisEntity> findById(UUID id);
}
