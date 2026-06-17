package ht.oni.cin.infrastructure.persistence.entity;

import ht.oni.cin.domain.model.IdentiteStatut;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cin_identite")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CinIdentiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 13)
    private String nin;

    @Column(name = "nin_display", length = 13)
    private String ninDisplay;

    @Column(name = "numero_carte", length = 20)
    private String numeroCarte;

    @Column(length = 10)
    private String nationalite;

    @Column(nullable = false, length = 100)
    private String nom;

    @Column(nullable = false, length = 200)
    private String prenom;

    @Column(name = "date_naissance", nullable = false)
    private LocalDate dateNaissance;

    @Column(name = "lieu_naissance", nullable = false, length = 200)
    private String lieuNaissance;

    @Column(nullable = false, length = 1)
    private String sexe;

    @Column(columnDefinition = "TEXT")
    private String adresse;

    @Column(length = 50)
    private String departement;

    @Column(name = "date_emission", nullable = false)
    private LocalDate dateEmission;

    @Column(name = "date_expiration", nullable = false)
    private LocalDate dateExpiration;

    @Column(name = "photo_ref", length = 500)
    private String photoRef;

    @Column(name = "score_ocr_moyen", precision = 5, scale = 2)
    private BigDecimal scoreOcrMoyen;

    @Column(name = "validee_par", nullable = false, length = 100)
    private String valideePar;

    @Column(name = "validee_le", nullable = false)
    private Instant valideeLe;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "modifie_le")
    private Instant modifieLe;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdentiteStatut statut;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(name = "consentement_documente", nullable = false)
    private boolean consentementDocumente;

    @Column(name = "base_legale", length = 100)
    private String baseLegale;

    @PrePersist
    void onCreate() {
        if (creeLe == null) {
            creeLe = Instant.now();
        }
        if (statut == null) {
            statut = IdentiteStatut.ACTIF;
        }
    }
}
