package ht.oni.cin.application.service;

import ht.oni.cin.config.CinProperties;
import ht.oni.cin.domain.model.AuditEventType;
import ht.oni.cin.domain.model.OcrExtractionResult;
import ht.oni.cin.domain.model.SessionStatus;
import ht.oni.cin.infrastructure.session.SessionStore;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String SESSION_PREFIX = "cin:session:";
    private static final String OPERATOR_SESSIONS_PREFIX = "cin:operator:sessions:";

    private final SessionStore sessionStore;
    private final CinProperties properties;
    private final EncryptionService encryptionService;
    private final AuditService auditService;

    public SessionData openSession(String operatorId, String ipAddress) {
        enforceSessionLimit(operatorId, ipAddress);

        UUID sessionId = UUID.randomUUID();
        String sessionToken = UUID.randomUUID().toString();

        SessionData session = new SessionData();
        session.setSessionId(sessionId);
        session.setSessionToken(sessionToken);
        session.setOperatorId(operatorId);
        session.setStatus(SessionStatus.OPEN);
        session.setCreatedAt(Instant.now());
        session.setExpiresAt(Instant.now().plus(Duration.ofMinutes(properties.getSession().getTtlMinutes())));

        long ttlSeconds = properties.getSession().getTtlMinutes() * 60L;
        String key = sessionKey(sessionId);
        sessionStore.save(key, session, ttlSeconds);
        sessionStore.addToSet(operatorSessionsKey(operatorId), sessionId.toString(), ttlSeconds);

        auditService.log(AuditEventType.SESSION_OPENED, operatorId, "OPERATOR",
                null, sessionId, null, null,
                Map.of("status", SessionStatus.OPEN.name()), ipAddress);

        return session;
    }

    public SessionData getSession(UUID sessionId) {
        SessionData session = sessionStore.get(sessionKey(sessionId));
        if (session == null) {
            throw new SessionNotFoundException("Session introuvable ou expirée: " + sessionId);
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            closeSession(sessionId, SessionStatus.EXPIRED, "system", null);
            throw new SessionExpiredException("Session expirée: " + sessionId);
        }
        return session;
    }

    public void verifySessionAccess(UUID sessionId, String operatorId) {
        SessionData session = getSession(sessionId);
        if (!session.getOperatorId().equals(operatorId)) {
            throw new SessionAccessDeniedException("Accès refusé à la session " + sessionId);
        }
    }

    public void storeEncryptedImage(UUID sessionId, byte[] imageBytes) {
        SessionData session = getSession(sessionId);
        session.setEncryptedImage(encryptionService.encrypt(Base64.getEncoder().encodeToString(imageBytes)));
        session.setStatus(SessionStatus.PROCESSING);
        saveSession(session);
    }

    public void storeOcrResults(UUID sessionId, OcrExtractionResult ocrResult, String photoBase64) {
        SessionData session = getSession(sessionId);
        session.setOcrResult(ocrResult);
        session.setEncryptedPhoto(photoBase64 != null ? encryptionService.encrypt(photoBase64) : null);
        session.setStatus(SessionStatus.AWAITING_VALIDATION);
        session.setIdempotencyToken(UUID.randomUUID().toString());
        saveSession(session);
    }

    public void closeSession(UUID sessionId, SessionStatus status, String actorId, String ipAddress) {
        SessionData session = sessionStore.get(sessionKey(sessionId));
        if (session != null) {
            sessionStore.removeFromSet(operatorSessionsKey(session.getOperatorId()), sessionId.toString());
            auditService.log(AuditEventType.SESSION_CLOSED, actorId, "OPERATOR",
                    null, sessionId, null,
                    Map.of("previousStatus", session.getStatus().name()),
                    Map.of("status", status.name()), ipAddress);
        }
        sessionStore.delete(sessionKey(sessionId));
    }

    public void saveSession(SessionData session) {
        long ttl = Duration.between(Instant.now(), session.getExpiresAt()).toSeconds();
        if (ttl > 0) {
            sessionStore.save(sessionKey(session.getSessionId()), session, ttl);
        }
    }

    private void enforceSessionLimit(String operatorId, String ipAddress) {
        Set<String> activeSessions = sessionStore.members(operatorSessionsKey(operatorId));
        int count = activeSessions.size();
        if (count >= properties.getSession().getMaxPerOperator()) {
            auditService.log(AuditEventType.SESSION_LIMIT_EXCEEDED, operatorId, "OPERATOR",
                    null, null, null, null,
                    Map.of("activeSessions", count, "limit", properties.getSession().getMaxPerOperator()),
                    ipAddress);
            throw new SessionLimitExceededException(
                    "Limite de " + properties.getSession().getMaxPerOperator() +
                            " sessions simultanées atteinte. Fermez une session existante ou attendez l'expiration.");
        }
    }

    private String sessionKey(UUID sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    private String operatorSessionsKey(String operatorId) {
        return OPERATOR_SESSIONS_PREFIX + operatorId;
    }

    @Data
    public static class SessionData {
        private UUID sessionId;
        private String sessionToken;
        private String operatorId;
        private SessionStatus status;
        private Instant createdAt;
        private Instant expiresAt;
        private String encryptedImage;
        private String encryptedPhoto;
        private OcrExtractionResult ocrResult;
        private String idempotencyToken;
    }

    public static class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(String message) { super(message); }
    }

    public static class SessionExpiredException extends RuntimeException {
        public SessionExpiredException(String message) { super(message); }
    }

    public static class SessionAccessDeniedException extends RuntimeException {
        public SessionAccessDeniedException(String message) { super(message); }
    }

    public static class SessionLimitExceededException extends RuntimeException {
        public SessionLimitExceededException(String message) { super(message); }
    }
}
