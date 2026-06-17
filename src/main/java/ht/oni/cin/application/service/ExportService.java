package ht.oni.cin.application.service;

import ht.oni.cin.api.dto.ExportIdentiteResponse;
import ht.oni.cin.domain.model.AuditEventType;
import ht.oni.cin.infrastructure.persistence.entity.CinApiClientEntity;
import ht.oni.cin.infrastructure.persistence.entity.CinIdentiteEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final IdentiteService identiteService;
    private final AuditService auditService;

    public ExportIdentiteResponse exportByNin(String nin, CinApiClientEntity client,
                                               boolean includePhoto, String ipAddress) {
        if (includePhoto && !client.isCanAccessPhoto()) {
            throw new ExportDeniedException("Accès photo non autorisé pour ce client API");
        }

        CinIdentiteEntity identite = identiteService.findByNin(nin);
        if (!identite.isConsentementDocumente()) {
            throw new ExportDeniedException("Consentement ou base légale non documentée pour ce titulaire");
        }

        Set<String> allowed = Arrays.stream(client.getAllowedFields()).collect(Collectors.toSet());
        Map<String, Object> data = filterFields(identite, allowed, includePhoto);

        auditService.log(AuditEventType.DATA_EXPORTED, client.getClientName(), "API_CLIENT",
                nin, identite.getSessionId(), client.getClientName(), null,
                Map.of("fields", allowed, "photo", includePhoto), ipAddress);

        return ExportIdentiteResponse.builder()
                .nin(nin)
                .data(data)
                .exportedAt(java.time.Instant.now())
                .build();
    }

    private Map<String, Object> filterFields(CinIdentiteEntity e, Set<String> allowed, boolean includePhoto) {
        Map<String, Object> all = new HashMap<>();
        all.put("nin", e.getNin());
        all.put("nin_display", e.getNinDisplay());
        all.put("numero_carte", e.getNumeroCarte());
        all.put("nationalite", e.getNationalite());
        all.put("nom", e.getNom());
        all.put("prenom", e.getPrenom());
        all.put("date_naissance", e.getDateNaissance().toString());
        all.put("lieu_naissance", e.getLieuNaissance());
        all.put("sexe", e.getSexe());
        all.put("adresse", e.getAdresse());
        all.put("departement", e.getDepartement());
        all.put("date_emission", e.getDateEmission().toString());
        all.put("date_expiration", e.getDateExpiration().toString());
        if (includePhoto) all.put("photo_ref", e.getPhotoRef());

        Map<String, Object> filtered = new HashMap<>();
        for (String field : allowed) {
            if (all.containsKey(field)) {
                filtered.put(field, all.get(field));
            }
        }
        return filtered;
    }

    public ExportIdentiteResponse exportByUuid(UUID id, CinApiClientEntity client,
                                                boolean includePhoto, String ipAddress) {
        CinIdentiteEntity identite = identiteService.findById(id);
        return exportByNin(identite.getNin(), client, includePhoto, ipAddress);
    }

    public static class ExportDeniedException extends RuntimeException {
        public ExportDeniedException(String message) { super(message); }
    }
}
