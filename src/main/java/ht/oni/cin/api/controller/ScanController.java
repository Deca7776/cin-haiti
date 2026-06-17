package ht.oni.cin.api.controller;

import ht.oni.cin.api.dto.*;
import ht.oni.cin.application.service.ScanService;
import ht.oni.cin.infrastructure.persistence.entity.CinIdentiteEntity;
import ht.oni.cin.security.OperatorPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scan")
@RequiredArgsConstructor
@Tag(name = "Numérisation", description = "Sessions de scan et validation CIN")
public class ScanController {

    private final ScanService scanService;

    @PostMapping("/sessions")
    @Operation(summary = "EF-01 — Ouvrir une session de numérisation")
    public ResponseEntity<SessionResponse> openSession(
            @AuthenticationPrincipal OperatorPrincipal principal,
            HttpServletRequest request) {
        return ResponseEntity.ok(scanService.openSession(principal.getOperatorId(), request.getRemoteAddr()));
    }

    @PostMapping(value = "/{sessionId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "EF-03/04/05 — Upload image et traitement OCR")
    public ResponseEntity<ScanResultsResponse> uploadImage(
            @PathVariable UUID sessionId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal OperatorPrincipal principal,
            HttpServletRequest request) throws Exception {
        return ResponseEntity.ok(scanService.processImage(sessionId, principal.getOperatorId(), file, request.getRemoteAddr()));
    }

    @GetMapping("/{sessionId}/results")
    @Operation(summary = "Récupérer les résultats OCR pour validation")
    public ResponseEntity<ScanResultsResponse> getResults(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal OperatorPrincipal principal) {
        return ResponseEntity.ok(scanService.getResults(sessionId, principal.getOperatorId()));
    }

    @PostMapping("/{sessionId}/validate")
    @Operation(summary = "EF-07 — Valider et enregistrer définitivement")
    public ResponseEntity<IdentiteSavedResponse> validate(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ValidateIdentiteRequest body,
            @AuthenticationPrincipal OperatorPrincipal principal,
            HttpServletRequest request) {
        CinIdentiteEntity saved = scanService.validateAndSave(sessionId, principal.getOperatorId(), body, request.getRemoteAddr());
        return ResponseEntity.ok(IdentiteSavedResponse.builder()
                .id(saved.getId())
                .nin(saved.getNin())
                .ninDisplay(saved.getNinDisplay())
                .numeroCarte(saved.getNumeroCarte())
                .prenom(saved.getPrenom())
                .nom(saved.getNom())
                .message("Identité enregistrée — session fermée, audit tracé")
                .nextStep("Export tiers : GET /api/v1/identites/{nin} avec JWT + X-API-Key")
                .build());
    }

    @PostMapping("/{sessionId}/cancel")
    @Operation(summary = "EF-02 — Annuler la session")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal OperatorPrincipal principal,
            HttpServletRequest request) {
        scanService.cancelSession(sessionId, principal.getOperatorId(), request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
