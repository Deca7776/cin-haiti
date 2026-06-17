package ht.oni.cin.api.controller;

import ht.oni.cin.application.service.InstanceRegistryService;
import ht.oni.cin.security.JwtValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevController {

    private final JwtValidator jwtValidator;
    private final InstanceRegistryService instanceRegistryService;

    @GetMapping("/token")
    public Map<String, String> getDevToken(@RequestParam(defaultValue = "operatrice-demo") String operatorId) {
        return Map.of(
                "token", jwtValidator.generateDevToken(operatorId),
                "operatorId", operatorId,
                "usage", "Authorization: Bearer <token>"
        );
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
