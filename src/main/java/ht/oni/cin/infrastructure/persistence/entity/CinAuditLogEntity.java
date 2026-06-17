package ht.oni.cin.infrastructure.persistence.entity;

import ht.oni.cin.domain.model.AuditEventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "cin_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CinAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private AuditEventType eventType;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "actor_type", nullable = false, length = 30)
    private String actorType;

    @Column(length = 13)
    private String nin;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "target_system", length = 100)
    private String targetSystem;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_before")
    private Map<String, Object> payloadBefore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_after")
    private Map<String, Object> payloadAfter;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(nullable = false, length = 128)
    private String signature;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
