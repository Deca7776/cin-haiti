package ht.oni.cin.infrastructure.persistence.repository;

import ht.oni.cin.infrastructure.persistence.entity.CinApiClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CinApiClientRepository extends JpaRepository<CinApiClientEntity, UUID> {
    Optional<CinApiClientEntity> findByClientName(String clientName);
}
