package ht.oni.cin.api.controller;

import ht.oni.cin.api.dto.ExportIdentiteResponse;
import ht.oni.cin.application.service.ExportService;
import ht.oni.cin.application.service.RateLimitService;
import ht.oni.cin.infrastructure.persistence.entity.CinApiClientEntity;
import ht.oni.cin.security.ApiClientPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/identites")
@RequiredArgsConstructor
@Tag(name = "Export", description = "API d'export vers systèmes tiers (EF-10)")
public class ExportController {

    private final ExportService exportService;
    private final RateLimitService rateLimitService;

    @GetMapping("/{nin}")
    @Operation(summary = "Exporter les données CIN par NIN")
    public ResponseEntity<ExportIdentiteResponse> exportByNin(
            @PathVariable String nin,
            @RequestParam(defaultValue = "false") boolean photo,
            @AuthenticationPrincipal ApiClientPrincipal principal,
            HttpServletRequest request) {

        if (!rateLimitService.tryConsume(principal.getClientId(), request.getRemoteAddr())) {
            long retryAfter = rateLimitService.secondsUntilRefill(principal.getClientId());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
                    .build();
        }

        CinApiClientEntity client = principal.getClient();
        ExportIdentiteResponse response = exportService.exportByNin(nin, client, photo, request.getRemoteAddr());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/uuid/{id}")
    @Operation(summary = "Exporter les données CIN par UUID interne")
    public ResponseEntity<ExportIdentiteResponse> exportByUuid(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean photo,
            @AuthenticationPrincipal ApiClientPrincipal principal,
            HttpServletRequest request) {

        if (!rateLimitService.tryConsume(principal.getClientId(), request.getRemoteAddr())) {
            long retryAfter = rateLimitService.secondsUntilRefill(principal.getClientId());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
                    .build();
        }

        var identite = exportService.exportByUuid(id, principal.getClient(), photo, request.getRemoteAddr());
        return ResponseEntity.ok(identite);
    }
}
