package ht.oni.cin.application.service;

import ht.oni.cin.domain.model.AuditEventType;
import ht.oni.cin.infrastructure.persistence.entity.CinAuditLogEntity;
import ht.oni.cin.infrastructure.persistence.repository.CinAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final CinAuditLogRepository auditLogRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public void log(AuditEventType eventType, String actorId, String actorType,
                    String nin, UUID sessionId, String targetSystem,
                    Map<String, Object> before, Map<String, Object> after, String ipAddress) {
        String signaturePayload = eventType + "|" + actorId + "|" + nin + "|" + Instant.now().toEpochMilli();
        CinAuditLogEntity entry = CinAuditLogEntity.builder()
                .eventType(eventType)
                .actorId(actorId)
                .actorType(actorType)
                .nin(nin)
                .sessionId(sessionId)
                .targetSystem(targetSystem)
                .payloadBefore(before)
                .payloadAfter(after)
                .ipAddress(ipAddress)
                .signature(encryptionService.sha256(signaturePayload))
                .createdAt(Instant.now())
                .build();
        auditLogRepository.save(entry);
    }
}
