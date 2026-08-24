package ht.oni.cin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "cin")
public class CinProperties {

    private Session session = new Session();
    private Ocr ocr = new Ocr();
    private RateLimit rateLimit = new RateLimit();
    private Encryption encryption = new Encryption();
    private Jwt jwt = new Jwt();
    private Minio minio = new Minio();
    private Instances instances = new Instances();
    private Integration integration = new Integration();

    @Getter
    @Setter
    public static class Session {
        private int ttlMinutes = 10;
        private int maxPerOperator = 10;
    }

    @Getter
    @Setter
    public static class Ocr {
        private double confidenceThreshold = 80.0;
        private String tesseractDataPath = "";
        private String language = "fra";
        private String serviceUrl = "";
        private int serviceTimeoutMs = 3000;
        private boolean localFallback = true;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private int apiRequestsPerMinute = 100;
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Encryption {
        private String aesKey;
    }

    @Getter
    @Setter
    public static class Jwt {
        private String publicKeyPath = "";
        private String issuer = "cin-iam";
    }

    @Getter
    @Setter
    public static class Minio {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "cin-photos";
    }

    @Getter
    @Setter
    public static class Instances {
        private int maxAllowed = 5;
        private String registrationToken = "change-me";
    }

    /**
     * Origines autorisées à appeler l'API depuis un navigateur (CORS) — configurable pour que
     * ce microservice puisse être embarqué (widget en iframe, appel fetch direct) depuis le
     * domaine d'une app hôte tierce, sans avoir à modifier le code pour chaque intégration.
     */
    @Getter
    @Setter
    public static class Integration {
        private List<String> allowedOrigins = List.of(
                "http://localhost:5173", "http://localhost:3000", "http://localhost:8080", "http://localhost:8081");
    }
}
