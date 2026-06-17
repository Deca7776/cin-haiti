package ht.oni.cin.api.dto;

import ht.oni.cin.application.service.SessionService;
import ht.oni.cin.domain.model.SessionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SessionResponse {
    private UUID sessionId;
    private String sessionToken;
    private SessionStatus status;
    private Instant expiresAt;

    public static SessionResponse from(SessionService.SessionData session) {
        return SessionResponse.builder()
                .sessionId(session.getSessionId())
                .sessionToken(session.getSessionToken())
                .status(session.getStatus())
                .expiresAt(session.getExpiresAt())
                .build();
    }
}
