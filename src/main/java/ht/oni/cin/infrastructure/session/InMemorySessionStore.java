package ht.oni.cin.infrastructure.session;

import ht.oni.cin.application.service.SessionService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Profile("local")
public class InMemorySessionStore implements SessionStore {

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> operatorSessions = new ConcurrentHashMap<>();

    @Override
    public void save(String key, SessionService.SessionData data, long ttlSeconds) {
        sessions.put(key, new Entry(data, Instant.now().plusSeconds(ttlSeconds)));
    }

    @Override
    public SessionService.SessionData get(String key) {
        Entry entry = sessions.get(key);
        if (entry == null) return null;
        if (entry.expiresAt.isBefore(Instant.now())) {
            sessions.remove(key);
            return null;
        }
        return entry.data;
    }

    @Override
    public void delete(String key) {
        sessions.remove(key);
    }

    @Override
    public Set<String> members(String operatorKey) {
        return operatorSessions.getOrDefault(operatorKey, Set.of());
    }

    @Override
    public void addToSet(String operatorKey, String sessionId, long ttlSeconds) {
        operatorSessions.compute(operatorKey, (k, v) -> {
            Set<String> set = ConcurrentHashMap.newKeySet();
            if (v != null) set.addAll(v);
            set.add(sessionId);
            return set;
        });
    }

    @Override
    public void removeFromSet(String operatorKey, String sessionId) {
        operatorSessions.computeIfPresent(operatorKey, (k, v) -> {
            Set<String> copy = ConcurrentHashMap.newKeySet();
            copy.addAll(v);
            copy.remove(sessionId);
            return copy.isEmpty() ? null : copy;
        });
    }

    private record Entry(SessionService.SessionData data, Instant expiresAt) {}
}
