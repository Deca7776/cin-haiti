package ht.oni.cin.security;

import ht.oni.cin.infrastructure.persistence.entity.CinApiClientEntity;
import ht.oni.cin.infrastructure.persistence.repository.CinApiClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@RequiredArgsConstructor
public class DualAuthFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";
    private static final String AUTH_HEADER = "Authorization";

    private final CinApiClientRepository apiClientRepository;
    private final JwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/api/v1/identites")) {
            authenticateApiClient(request, response, filterChain);
        } else if (path.startsWith("/api/v1/scan")) {
            authenticateOperator(request, response, filterChain);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private void authenticateOperator(HttpServletRequest request, HttpServletResponse response,
                                      FilterChain chain) throws ServletException, IOException {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing instanceof OperatorPrincipal) {
            chain.doFilter(request, response);
            return;
        }

        String auth = request.getHeader(AUTH_HEADER);
        if (auth == null || !auth.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT requis (Bearer token)");
            return;
        }
        String token = auth.substring(7);
        String operatorId;
        try {
            operatorId = jwtValidator.validateAndExtractSubject(token);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "JWT invalide ou expiré");
            return;
        }
        // En dehors du try/catch : une exception levee plus loin dans la chaine (controleur, service)
        // ne doit pas etre faussement rapportee comme un echec d'authentification JWT.
        SecurityContextHolder.getContext().setAuthentication(new OperatorPrincipal(operatorId, token));
        chain.doFilter(request, response);
    }

    private void authenticateApiClient(HttpServletRequest request, HttpServletResponse response,
                                       FilterChain chain) throws ServletException, IOException {
        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing instanceof ApiClientPrincipal) {
            chain.doFilter(request, response);
            return;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        String auth = request.getHeader(AUTH_HEADER);

        if (apiKey == null || auth == null || !auth.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "X-API-Key et JWT requis");
            return;
        }

        String token = auth.substring(7);
        ApiClientPrincipal principal;
        try {
            jwtValidator.validateAndExtractSubject(token);
            String hash = sha256(apiKey);
            CinApiClientEntity client = apiClientRepository.findAll().stream()
                    .filter(c -> c.getApiKeyHash().equals(hash) && c.isActive())
                    .filter(c -> c.getExpiresAt().isAfter(Instant.now()))
                    .findFirst()
                    .orElseThrow(() -> new SecurityException("Clé API invalide"));
            principal = new ApiClientPrincipal(client, apiKey);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentification système tiers échouée");
            return;
        }
        // En dehors du try/catch : une exception levee plus loin dans la chaine ne doit pas etre
        // faussement rapportee comme un echec d'authentification.
        SecurityContextHolder.getContext().setAuthentication(principal);
        chain.doFilter(request, response);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/actuator/health")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/v3/api-docs");
    }
}
