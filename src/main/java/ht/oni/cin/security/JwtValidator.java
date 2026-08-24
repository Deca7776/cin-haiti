package ht.oni.cin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * Validateur JWT à deux modes, selon la configuration de déploiement :
 *
 * <p><b>Mode production (IAM externe)</b> — si {@code cin.jwt.public-key-path} pointe vers une
 * clé publique RSA au format PEM, les jetons sont vérifiés en RS256 avec cette clé : l'app hôte
 * qui intègre ce microservice fait signer ses JWT opératrice par sa propre IAM (RS256, clé privée
 * côté IAM) et ce service les valide sans jamais voir le secret. C'est le seul mode accepté dès
 * que la clé publique est configurée — aucun repli silencieux vers le secret de démo.</p>
 *
 * <p><b>Mode démo/local (par défaut)</b> — sans clé publique configurée, on retombe sur un HMAC
 * signé avec {@code cin.jwt.dev-secret}, généré par {@link #generateDevToken}
 * (endpoint {@code GET /api/v1/dev/token}). Uniquement pour le développement local / les démos —
 * ne jamais utiliser tel quel en production, d'où l'avertissement au démarrage.</p>
 */
@Component
@Slf4j
public class JwtValidator {

    private final SecretKey devSecretKey;
    private final PublicKey productionPublicKey;

    public JwtValidator(
            @Value("${cin.jwt.dev-secret:DevSecretKeyForCINDemoOnlyChangeInProduction123456}") String devSecret,
            @Value("${cin.jwt.public-key-path:}") String publicKeyPath) {
        this.devSecretKey = buildHmacKey(devSecret);
        this.productionPublicKey = publicKeyPath == null || publicKeyPath.isBlank()
                ? null
                : loadRsaPublicKey(publicKeyPath);

        if (productionPublicKey != null) {
            log.info("JwtValidator en mode production — jetons vérifiés en RS256 avec la clé publique {}", publicKeyPath);
        } else {
            log.warn("cin.jwt.public-key-path non configuré — JwtValidator n'accepte que les jetons de démo "
                    + "(HMAC, JWT_DEV_SECRET). À configurer avec la clé publique de l'IAM de l'app hôte avant "
                    + "toute intégration en production.");
        }
    }

    private static SecretKey buildHmacKey(String devSecret) {
        byte[] keyBytes = devSecret.getBytes(StandardCharsets.UTF_8);
        byte[] padded = new byte[32];
        System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
        return Keys.hmacShaKeyFor(padded);
    }

    private static PublicKey loadRsaPublicKey(String path) {
        try {
            String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            String base64Body = pem
                    .replaceAll("-{2,}BEGIN [^-]+-{2,}", "")
                    .replaceAll("-{2,}END [^-]+-{2,}", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(base64Body);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(der));
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException("Impossible de charger la clé publique JWT (RS256) depuis " + path, e);
        }
    }

    public String validateAndExtractSubject(String token) {
        JwtParser parser = productionPublicKey != null
                ? Jwts.parser().verifyWith(productionPublicKey).build()
                : Jwts.parser().verifyWith(devSecretKey).build();

        Claims claims = parser.parseSignedClaims(token).getPayload();

        if (claims.getExpiration() != null && claims.getExpiration().before(new Date())) {
            throw new SecurityException("Token expiré");
        }
        return claims.getSubject();
    }

    /** Vrai si une clé publique RS256 est configurée — les jetons de démo HMAC ne sont alors plus
     * acceptés (cf. {@code DevController} qui refuse /dev/token dans ce cas). */
    public boolean isProductionMode() {
        return productionPublicKey != null;
    }

    /** Utilitaire dev — génère un JWT de démonstration (mode HMAC uniquement). */
    public String generateDevToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 8 * 3600_000L))
                .signWith(devSecretKey)
                .compact();
    }
}
