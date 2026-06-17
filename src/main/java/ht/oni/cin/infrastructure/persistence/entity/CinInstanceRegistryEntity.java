package ht.oni.cin.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cin_instance_registry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CinInstanceRegistryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "instance_id", nullable = false, unique = true, length = 100)
    private String instanceId;

    @Column(nullable = false, length = 200)
    private String hostname;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_heartbeat", nullable = false)
    private Instant lastHeartbeat;

    @Column(nullable = false)
    private boolean active;
}
