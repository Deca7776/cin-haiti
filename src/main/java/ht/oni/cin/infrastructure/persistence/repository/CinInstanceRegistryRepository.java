package ht.oni.cin.infrastructure.persistence.repository;

import ht.oni.cin.infrastructure.persistence.entity.CinInstanceRegistryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CinInstanceRegistryRepository extends JpaRepository<CinInstanceRegistryEntity, UUID> {
    Optional<CinInstanceRegistryEntity> findByInstanceId(String instanceId);
    long countByActiveTrue();

    @Query("SELECT COUNT(i) FROM CinInstanceRegistryEntity i WHERE i.active = true")
    long countActiveInstances();
}
