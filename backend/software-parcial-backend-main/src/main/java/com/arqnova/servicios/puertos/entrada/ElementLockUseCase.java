package com.arqnova.servicios.puertos.entrada;
import com.arqnova.modelo.excepciones.UnauthorizedActionException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
public interface ElementLockUseCase {
 AcquireResult acquire(String projectId, String sessionId, String elementId,
                                               String elementType, String userId, String userName,
                                               String socketId) ;
 ElementLock requireOwnerAndRenew(String sessionId, String elementId,
                                                          String elementType, String userId, String socketId) ;
 Optional<ElementLock> release(String sessionId, String elementId,
                                                       String elementType, String userId, String socketId) ;
 List<ElementLock> releaseBySocket(String socketId) ;
 List<ElementLock> expireNow() ;
 Optional<ElementLock> find(String sessionId, String elementType, String elementId) ;
    public record AcquireResult(boolean granted, ElementLock lock) {}
    public record ElementLock(String projectId, String sessionId, String elementId, String elementType,
                              String userId, String userName, String socketId, long lockedAt, long expiresAt) {}
}
