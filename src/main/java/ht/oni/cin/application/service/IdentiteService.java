package ht.oni.cin.application.service;

import ht.oni.cin.api.dto.ValidateIdentiteRequest;
import ht.oni.cin.domain.model.AuditEventType;
import ht.oni.cin.domain.model.IdentiteStatut;
import ht.oni.cin.domain.model.SessionStatus;
import ht.oni.cin.infrastructure.persistence.entity.CinIdentiteEntity;
import ht.oni.cin.infrastructure.persistence.repository.CinIdentiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentiteService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CinIdentiteRepository identiteRepository;
    private final EncryptionService encryptionService;
    private final PhotoStorage photoStorage;
    private final AuditService auditService;

    @Transactional
    public CinIdentiteEntity saveValidated(ValidateIdentiteRequest request, SessionService.SessionData session,
                                             String operatorId, String ipAddress) {
        String nin = normalizeNin(request.getNin());
        if (identiteRepository.existsByNin(nin)) {
            throw new DuplicateNinException("Le NIN " + nin + " existe déjà en base");
        }

        String photoRef = null;
        if (request.getPhotoBase64() != null && !request.getPhotoBase64().isBlank()) {
            photoRef = photoStorage.storePhoto(nin, request.getPhotoBase64());
        } else if (session.getEncryptedPhoto() != null) {
            String decrypted = encryptionService.decrypt(session.getEncryptedPhoto());
            photoRef = photoStorage.storePhoto(nin, decrypted);
        }

        CinIdentiteEntity entity = CinIdentiteEntity.builder()
                .nin(nin)
                .ninDisplay(request.getNinDisplay() != null ? request.getNinDisplay() : trimLeadingZeros(nin))
                .numeroCarte(request.getNumeroCarte())
                .nationalite(request.getNationalite() != null ? request.getNationalite() : "HTI")
                .nom(request.getNom().toUpperCase())
                .prenom(request.getPrenom())
                .dateNaissance(parseDate(request.getDateNaissance()))
                .lieuNaissance(request.getLieuNaissance())
                .sexe(request.getSexe())
                .adresse(request.getAdresse())
                .departement(request.getDepartement())
                .dateEmission(parseDate(request.getDateEmission()))
                .dateExpiration(parseDate(request.getDateExpiration()))
                .photoRef(photoRef)
                .scoreOcrMoyen(request.getScoreOcrMoyen() != null
                        ? BigDecimal.valueOf(request.getScoreOcrMoyen()).setScale(2, RoundingMode.HALF_UP) : null)
                .valideePar(operatorId)
                .valideeLe(Instant.now())
                .sessionId(session.getSessionId())
                .statut(IdentiteStatut.ACTIF)
                .consentementDocumente(request.isConsentementDocumente())
                .baseLegale(request.getBaseLegale())
                .build();

        entity.setChecksum(computeChecksum(entity));
        CinIdentiteEntity saved = identiteRepository.save(entity);

        auditService.log(AuditEventType.VALIDATION_CONFIRMED, operatorId, "OPERATOR",
                saved.getNin(), session.getSessionId(), null, null,
                Map.of("id", saved.getId().toString(), "statut", SessionStatus.COMPLETED.name()), ipAddress);

        return saved;
    }

    public CinIdentiteEntity findByNin(String nin) {
        return identiteRepository.findByNin(nin)
                .orElseThrow(() -> new IdentiteNotFoundException("Identité non trouvée pour NIN: " + nin));
    }

    public CinIdentiteEntity findById(UUID id) {
        return identiteRepository.findById(id)
                .orElseThrow(() -> new IdentiteNotFoundException("Identité non trouvée: " + id));
    }

    private LocalDate parseDate(String date) {
        return LocalDate.parse(date, DATE_FMT);
    }

    private String normalizeNin(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 13) {
            return String.format("%013d", Long.parseLong(digits));
        }
        return digits.length() > 13 ? digits.substring(0, 13) : digits;
    }

    private String trimLeadingZeros(String nin) {
        String trimmed = nin.replaceFirst("^0+(?!$)", "");
        return trimmed.length() >= 10 ? trimmed : nin;
    }

    private String computeChecksum(CinIdentiteEntity e) {
        String payload = e.getNin() + "|" + e.getNom() + "|" + e.getPrenom() + "|" + e.getDateNaissance();
        return encryptionService.sha256(payload);
    }

    public static class DuplicateNinException extends RuntimeException {
        public DuplicateNinException(String message) { super(message); }
    }

    public static class IdentiteNotFoundException extends RuntimeException {
        public IdentiteNotFoundException(String message) { super(message); }
    }
}
