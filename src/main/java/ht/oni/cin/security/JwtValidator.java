package ht.oni.cin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Validateur JWT — en production : clé publique RS256 de l'IAM externe.
 * Mode dev : HMAC pour démonstration locale.
 */
@Component
public class JwtValidator {

    private final SecretKey secretKey;

    public JwtValidator(@Value("${cin.jwt.dev-secret:DevSecretKeyForCINDemoOnlyChangeInProduction123456}") String devSecret) {
        byte[] keyBytes = devSecret.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[32];
        System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
        this.secretKey = Keys.hmacShaKeyFor(padded);
    }

    public String validateAndExtractSubject(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (claims.getExpiration() != null && claims.getExpiration().before(new Date())) {
            throw new SecurityException("Token expiré");
        }
        return claims.getSubject();
    }

    /** Utilitaire dev — génère un JWT de démonstration */
    public String generateDevToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 8 * 3600_000L))
                .signWith(secretKey)
                .compact();
    }
}
