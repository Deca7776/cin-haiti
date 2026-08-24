package ht.oni.cin.api.controller;

import ht.oni.cin.application.service.InstanceRegistryService;
import ht.oni.cin.security.JwtValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevController {

    private final JwtValidator jwtValidator;
    private final InstanceRegistryService instanceRegistryService;

    @GetMapping("/token")
    public ResponseEntity<Map<String, Object>> getDevToken(@RequestParam(defaultValue = "operatrice-demo") String operatorId) {
        // Dès qu'une clé publique RS256 est configurée (mode production, cf. JwtValidator), les
        // jetons de démo HMAC générés ici ne seraient plus acceptés par /api/v1/scan/** — mieux
        // vaut le refuser explicitement ici que laisser l'app hôte découvrir un 401 plus tard.
        if (jwtValidator.isProductionMode()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "DEV_TOKEN_DISABLED",
                    "message", "Mode production (RS256) actif — /dev/token est désactivé, l'app hôte doit signer ses propres JWT avec l'IAM configurée",
                    "timestamp", Instant.now().toString()
            ));
        }
        return ResponseEntity.ok(Map.of(
                "token", jwtValidator.generateDevToken(operatorId),
                "operatorId", operatorId,
                "usage", "Authorization: Bearer <token>"
        ));
    }

    @PostMapping("/instances/register")
    public Map<String, Object> registerInstance(@RequestHeader("X-Registration-Token") String token) {
        instanceRegistryService.register(token);
        return Map.of(
                "registered", true,
                "activeInstances", instanceRegistryService.getActiveInstanceCount(),
                "maxAllowed", instanceRegistryService.getMaxAllowed()
        );
    }
}
