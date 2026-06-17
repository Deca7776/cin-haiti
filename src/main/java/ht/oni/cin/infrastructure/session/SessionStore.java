package ht.oni.cin.infrastructure.session;

import ht.oni.cin.application.service.SessionService;

import java.util.Set;

public interface SessionStore {

    void save(String key, SessionService.SessionData data, long ttlSeconds);

    SessionService.SessionData get(String key);

    void delete(String key);

    Set<String> members(String operatorKey);

    void addToSet(String operatorKey, String sessionId, long ttlSeconds);

    void removeFromSet(String operatorKey, String sessionId);
}
