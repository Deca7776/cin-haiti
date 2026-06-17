package ht.oni.cin.infrastructure.session;

import ht.oni.cin.application.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Profile("!local")
@RequiredArgsConstructor
public class RedisSessionStore implements SessionStore {

    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void save(String key, SessionService.SessionData data, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, data, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public SessionService.SessionData get(String key) {
        return (SessionService.SessionData) redisTemplate.opsForValue().get(key);
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public Set<String> members(String operatorKey) {
        Set<Object> raw = redisTemplate.opsForSet().members(operatorKey);
        if (raw == null) return Set.of();
        return raw.stream().map(Object::toString).collect(Collectors.toSet());
    }

    @Override
    public void addToSet(String operatorKey, String sessionId, long ttlSeconds) {
        redisTemplate.opsForSet().add(operatorKey, sessionId);
        redisTemplate.expire(operatorKey, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void removeFromSet(String operatorKey, String sessionId) {
        redisTemplate.opsForSet().remove(operatorKey, sessionId);
    }
}
