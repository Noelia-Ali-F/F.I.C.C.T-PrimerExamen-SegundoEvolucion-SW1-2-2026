package com.arqnova.service;

import com.arqnova.exception.UnauthorizedActionException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UmlElementLockServiceTest {
    @Test
    void grantsFirstUserAndDeniesSecondUserWithOwnerIdentity() {
        UmlElementLockService service = new UmlElementLockService(45_000, Clock.systemUTC());

        var first = service.acquire("p1", "s1", "user", "class", "maria", "María López", "socket-a");
        var second = service.acquire("p1", "s1", "user", "class", "carlos", "Carlos Ramírez", "socket-b");

        assertTrue(first.granted());
        assertFalse(second.granted());
        assertEquals("María López", second.lock().userName());
    }

    @Test
    void rejectsMutationWithoutOwningTheElementLock() {
        UmlElementLockService service = new UmlElementLockService(45_000, Clock.systemUTC());
        service.acquire("p1", "s1", "r1", "relation", "maria", "María López", "socket-a");

        var error = assertThrows(UnauthorizedActionException.class,
                () -> service.requireOwnerAndRenew("s1", "r1", "relation", "carlos", "socket-b"));

        assertTrue(error.getMessage().contains("María López"));
    }

    @Test
    void ownerCanReleaseAndAnotherUserCanAcquireImmediately() {
        UmlElementLockService service = new UmlElementLockService(45_000, Clock.systemUTC());
        service.acquire("p1", "s1", "attr-1", "attribute", "maria", "María López", "socket-a");

        assertTrue(service.release("s1", "attr-1", "attribute", "maria", "socket-a").isPresent());
        assertTrue(service.acquire("p1", "s1", "attr-1", "attribute", "carlos", "Carlos Ramírez", "socket-b").granted());
    }

    @Test
    void disconnectReleasesEveryLockOwnedByTheSocket() {
        UmlElementLockService service = new UmlElementLockService(45_000, Clock.systemUTC());
        service.acquire("p1", "s1", "c1", "class", "maria", "María López", "socket-a");
        service.acquire("p1", "s1", "r1", "relation", "maria", "María López", "socket-a");

        assertEquals(2, service.releaseBySocket("socket-a").size());
        assertTrue(service.find("s1", "class", "c1").isEmpty());
        assertTrue(service.find("s1", "relation", "r1").isEmpty());
    }

    @Test
    void expiredLockIsRemovedAndCanBeAcquiredAgain() {
        MutableClock clock = new MutableClock(1_000);
        UmlElementLockService service = new UmlElementLockService(2_000, clock);
        service.acquire("p1", "s1", "c1", "class", "maria", "María López", "socket-a");
        clock.now = 3_001;

        assertEquals(1, service.expireNow().size());
        assertTrue(service.acquire("p1", "s1", "c1", "class", "carlos", "Carlos Ramírez", "socket-b").granted());
    }

    @Test
    void renewalKeepsTheLockBeyondItsOriginalExpiration() {
        MutableClock clock = new MutableClock(1_000);
        UmlElementLockService service = new UmlElementLockService(2_000, clock);
        service.acquire("p1", "s1", "c1", "class", "maria", "María", "socket-a");
        clock.now = 2_500;
        service.requireOwnerAndRenew("s1", "c1", "class", "maria", "socket-a");
        clock.now = 3_100;

        assertFalse(service.acquire("p1", "s1", "c1", "class", "carlos", "Carlos", "socket-b").granted());
    }

    @Test
    void sameElementIdInDifferentSessionsHasIndependentLocks() {
        UmlElementLockService service = new UmlElementLockService(45_000, Clock.systemUTC());
        assertTrue(service.acquire("p1", "s1", "user", "class", "maria", "María", "socket-a").granted());
        assertTrue(service.acquire("p2", "s2", "user", "class", "carlos", "Carlos", "socket-b").granted());
    }

    @Test
    void simultaneousAcquisitionHasExactlyOneWinner() throws Exception {
        UmlElementLockService service = new UmlElementLockService(45_000, Clock.systemUTC());
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        var a = pool.submit(() -> { start.await(); return service.acquire("p1", "s1", "c1", "class", "a", "A", "socket-a").granted(); });
        var b = pool.submit(() -> { start.await(); return service.acquire("p1", "s1", "c1", "class", "b", "B", "socket-b").granted(); });
        start.countDown();
        int granted = (a.get() ? 1 : 0) + (b.get() ? 1 : 0);
        pool.shutdownNow();
        assertEquals(1, granted);
    }

    @Test
    void sameAuthenticatedUserCannotImpersonateOwnerFromAnotherConnection() {
        UmlElementLockService service = new UmlElementLockService(45_000, Clock.systemUTC());
        service.acquire("p1", "s1", "c1", "class", "maria", "María", "socket-a");
        assertThrows(UnauthorizedActionException.class,
                () -> service.requireOwnerAndRenew("s1", "c1", "class", "maria", "socket-falsa"));
    }

    private static final class MutableClock extends Clock {
        private long now;
        private MutableClock(long now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now); }
    }
}
