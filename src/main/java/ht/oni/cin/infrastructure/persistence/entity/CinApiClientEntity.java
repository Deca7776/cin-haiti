package ht.oni.cin.infrastructure.persistence.entity;

import ht.oni.cin.infrastructure.persistence.converter.StringArrayConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cin_api_client")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CinApiClientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_name", nullable = false, unique = true, length = 100)
    private String clientName;

    @Column(name = "api_key_hash", nullable = false, length = 128)
    private String apiKeyHash;

    @Convert(converter = StringArrayConverter.class)
    @Column(name = "allowed_fields", nullable = false, length = 500)
    private String[] allowedFields;

    @Column(name = "can_access_photo", nullable = false)
    private boolean canAccessPhoto;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_rotated_at", nullable = false)
    private Instant lastRotatedAt;
}
