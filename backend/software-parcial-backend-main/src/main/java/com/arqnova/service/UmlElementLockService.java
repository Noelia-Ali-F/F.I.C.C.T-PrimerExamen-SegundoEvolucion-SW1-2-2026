package com.arqnova.service;

import com.arqnova.exception.UnauthorizedActionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UmlElementLockService {
    private final Map<LockKey, ElementLock> locks = new ConcurrentHashMap<>();
    private final long ttlMillis;
    private final Clock clock;

    @Autowired
    public UmlElementLockService(@Value("${uml.collaboration.lock-ttl-ms:45000}") long ttlMillis) {
        this(ttlMillis, Clock.systemUTC());
    }

    UmlElementLockService(long ttlMillis, Clock clock) {
        if (ttlMillis < 1000) throw new IllegalArgumentException("El TTL de bloqueos debe ser de al menos 1000 ms");
        this.ttlMillis = ttlMillis;
        this.clock = clock;
    }

    public synchronized AcquireResult acquire(String projectId, String sessionId, String elementId,
                                               String elementType, String userId, String userName,
                                               String socketId) {
        validate(elementId, elementType);
        expireNow();
        LockKey key = new LockKey(sessionId, elementType, elementId);
        ElementLock current = locks.get(key);
        long now = clock.millis();
        if (current != null && (!current.userId().equals(userId) || !current.socketId().equals(socketId)))
            return new AcquireResult(false, current);
        ElementLock granted = new ElementLock(projectId, sessionId, elementId, elementType, userId,
                userName, socketId, current == null ? now : current.lockedAt(), now + ttlMillis);
        locks.put(key, granted);
        return new AcquireResult(true, granted);
    }

    public synchronized ElementLock requireOwnerAndRenew(String sessionId, String elementId,
                                                          String elementType, String userId, String socketId) {
        validate(elementId, elementType);
        expireNow();
        LockKey key = new LockKey(sessionId, elementType, elementId);
        ElementLock current = locks.get(key);
        if (current == null || !current.userId().equals(userId) || !current.socketId().equals(socketId)) {
            String owner = current == null ? null : current.userName();
            throw new UnauthorizedActionException(owner == null
                    ? "Debes adquirir el bloqueo antes de modificar este elemento"
                    : "Este elemento está siendo editado por " + owner);
        }
        ElementLock renewed = new ElementLock(current.projectId(), current.sessionId(), current.elementId(),
                current.elementType(), current.userId(), current.userName(), current.socketId(),
                current.lockedAt(), clock.millis() + ttlMillis);
        locks.put(key, renewed);
        return renewed;
    }

    public synchronized Optional<ElementLock> release(String sessionId, String elementId,
                                                       String elementType, String userId, String socketId) {
        LockKey key = new LockKey(sessionId, elementType, elementId);
        ElementLock current = locks.get(key);
        if (current == null || !current.userId().equals(userId) || !current.socketId().equals(socketId)) return Optional.empty();
        locks.remove(key);
        return Optional.of(current);
    }

    public synchronized List<ElementLock> releaseBySocket(String socketId) {
        List<ElementLock> released = locks.values().stream().filter(lock -> lock.socketId().equals(socketId)).toList();
        released.forEach(lock -> locks.remove(new LockKey(lock.sessionId(), lock.elementType(), lock.elementId())));
        return released;
    }

    public synchronized List<ElementLock> expireNow() {
        long now = clock.millis();
        List<ElementLock> expired = new ArrayList<>(locks.values().stream()
                .filter(lock -> lock.expiresAt() <= now).toList());
        expired.forEach(lock -> locks.remove(new LockKey(lock.sessionId(), lock.elementType(), lock.elementId())));
        return expired;
    }

    public Optional<ElementLock> find(String sessionId, String elementType, String elementId) {
        expireNow();
        return Optional.ofNullable(locks.get(new LockKey(sessionId, elementType, elementId)));
    }

    private void validate(String elementId, String elementType) {
        if (elementId == null || elementId.isBlank() || elementType == null || elementType.isBlank())
            throw new IllegalArgumentException("El bloqueo requiere elementId y elementType");
    }

    private record LockKey(String sessionId, String elementType, String elementId) {}
    public record AcquireResult(boolean granted, ElementLock lock) {}
    public record ElementLock(String projectId, String sessionId, String elementId, String elementType,
                              String userId, String userName, String socketId, long lockedAt, long expiresAt) {}
}
