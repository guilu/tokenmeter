package dev.diegobarrioh.tokenmeter.infrastructure.persistence.pricing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelPricingJpaRepository extends JpaRepository<ModelPricingEntity, Long> {

  Optional<ModelPricingEntity> findByProviderAndModel(String provider, String model);

  List<ModelPricingEntity> findAllByOrderByProviderAscModelAsc();
}
