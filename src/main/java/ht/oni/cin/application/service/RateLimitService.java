package ht.oni.cin.application.service;

import ht.oni.cin.config.CinProperties;
import ht.oni.cin.domain.model.AuditEventType;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting : 100 req/min par clé API (CDC §5.1).
 * En cas de dépassement : HTTP 429 + audit + Retry-After.
 */
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final CinProperties properties;
    private final AuditService auditService;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public boolean tryConsume(String apiKeyId, String ipAddress) {
        if (!properties.getRateLimit().isEnabled()) {
            return true;
        }
        Bucket bucket = buckets.computeIfAbsent(apiKeyId, this::createBucket);
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            auditService.log(AuditEventType.RATE_LIMIT_EXCEEDED, apiKeyId, "API_CLIENT",
                    null, null, null, null,
                    Map.of("limit", properties.getRateLimit().getApiRequestsPerMinute(), "window", "1 minute"),
                    ipAddress);
        }
        return allowed;
    }

    public long secondsUntilRefill(String apiKeyId) {
        Bucket bucket = buckets.computeIfAbsent(apiKeyId, this::createBucket);
        return bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill() / 1_000_000_000;
    }

    private Bucket createBucket(String key) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(properties.getRateLimit().getApiRequestsPerMinute())
                .refillGreedy(properties.getRateLimit().getApiRequestsPerMinute(), Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
