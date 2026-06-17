package ht.oni.cin.application.service;

import ht.oni.cin.config.CinProperties;
import ht.oni.cin.domain.model.AuditEventType;
import ht.oni.cin.infrastructure.persistence.entity.CinInstanceRegistryEntity;
import ht.oni.cin.infrastructure.persistence.repository.CinInstanceRegistryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Contrôle du nombre d'instances déployées (réponse CDC [2]).
 */
@Service
@RequiredArgsConstructor
public class InstanceRegistryService {

    private final CinInstanceRegistryRepository repository;
    private final CinProperties properties;
    private final AuditService auditService;

    private String instanceId;

    @Transactional
    public void register(String registrationToken) {
        if (!properties.getInstances().getRegistrationToken().equals(registrationToken)) {
            throw new SecurityException("Jeton d'enregistrement d'instance invalide");
        }

        long active = repository.countActiveInstances();
        if (active >= properties.getInstances().getMaxAllowed()) {
            throw new IllegalStateException(
                    "Nombre maximal d'instances autorisées atteint (" + properties.getInstances().getMaxAllowed() + ")");
        }

        instanceId = UUID.randomUUID().toString();
        String hostname = resolveHostname();

        CinInstanceRegistryEntity entity = CinInstanceRegistryEntity.builder()
                .instanceId(instanceId)
                .hostname(hostname)
                .registeredAt(Instant.now())
                .lastHeartbeat(Instant.now())
                .active(true)
                .build();
        repository.save(entity);

        auditService.log(AuditEventType.INSTANCE_REGISTERED, instanceId, "SYSTEM",
                null, null, hostname, null,
                Map.of("maxAllowed", properties.getInstances().getMaxAllowed()), null);
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void heartbeat() {
        if (instanceId == null) return;
        repository.findByInstanceId(instanceId).ifPresent(entity -> {
            entity.setLastHeartbeat(Instant.now());
            repository.save(entity);
        });
    }

    public long getActiveInstanceCount() {
        return repository.countActiveInstances();
    }

    public int getMaxAllowed() {
        return properties.getInstances().getMaxAllowed();
    }

    private String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
