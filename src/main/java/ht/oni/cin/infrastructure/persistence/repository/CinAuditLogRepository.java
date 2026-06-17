package ht.oni.cin.infrastructure.persistence.repository;

import ht.oni.cin.infrastructure.persistence.entity.CinAuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CinAuditLogRepository extends JpaRepository<CinAuditLogEntity, Long> {
}
