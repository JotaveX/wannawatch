package br.com.jotave.wannawatch.tmdb.client;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** Espaça as chamadas para não passar de N requisições por segundo (compartilhado entre threads). */
class RequestThrottle {

    private final long intervalNanos;
    private long nextSlot = System.nanoTime();

    RequestThrottle(int requestsPerSecond) {
        this.intervalNanos = TimeUnit.SECONDS.toNanos(1) / Math.max(1, requestsPerSecond);
    }

    void acquire() {
        long wait;
        synchronized (this) {
            long now = System.nanoTime();
            long slot = Math.max(now, nextSlot);
            nextSlot = slot + intervalNanos;
            wait = slot - now;
        }
        if (wait > 0) {
            LockSupport.parkNanos(wait);
        }
    }
}
