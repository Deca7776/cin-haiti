package ht.oni.cin.application.service;

import ht.oni.cin.config.CinProperties;
import ht.oni.cin.domain.model.AuditEventType;
import ht.oni.cin.domain.model.OcrExtractionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SessionServiceTest {

    private ht.oni.cin.infrastructure.session.SessionStore sessionStore;
    private SessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionStore = mock(ht.oni.cin.infrastructure.session.SessionStore.class);
        CinProperties props = new CinProperties();
        props.getSession().setMaxPerOperator(10);
        props.getSession().setTtlMinutes(10);
        props.getEncryption().setAesKey("0123456789ABCDEF0123456789ABCDEF");

        sessionService = new SessionService(sessionStore, props, new EncryptionService(props), mock(AuditService.class));
    }

    @Test
    void openSession_createsSessionWhenUnderLimit() {
        when(sessionStore.members(anyString())).thenReturn(Set.of());
        SessionService.SessionData session = sessionService.openSession("op-1", "127.0.0.1");
        assertNotNull(session.getSessionId());
        assertEquals("op-1", session.getOperatorId());
        verify(sessionStore).save(startsWith("cin:session:"), any(), anyLong());
    }

    @Test
    void openSession_throwsWhenLimitExceeded() {
        when(sessionStore.members(anyString())).thenReturn(Set.of("s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10"));
        assertThrows(SessionService.SessionLimitExceededException.class,
                () -> sessionService.openSession("op-1", "127.0.0.1"));
    }
}
