package ht.oni.cin.infrastructure.persistence.repository;

import ht.oni.cin.infrastructure.persistence.entity.CinIdentiteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CinIdentiteRepository extends JpaRepository<CinIdentiteEntity, UUID> {
    Optional<CinIdentiteEntity> findByNin(String nin);
    boolean existsByNin(String nin);
}
