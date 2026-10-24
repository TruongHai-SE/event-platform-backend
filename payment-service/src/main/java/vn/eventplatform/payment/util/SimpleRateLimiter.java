package vn.eventplatform.payment.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token-bucket rate limiter nhẹ, luồng an toàn (thread-safe),
 * dùng để kiểm soát tần suất request sang các cổng thanh toán bên ngoài.
 */
@Slf4j
public class SimpleRateLimiter {

    private final double permitsPerSecond;
    private final AtomicLong nextFreeTicketNanos = new AtomicLong(0L);

    public SimpleRateLimiter(double permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond > 0 ? permitsPerSecond : 10.0;
    }

    /**
     * Chặn và chờ (sleep) cho đến khi permit tiếp theo sẵn sàng.
     */
    public void acquire() {
        if (permitsPerSecond <= 0) {
            return;
        }
        long intervalNanos = (long) (1_000_000_000.0 / permitsPerSecond);
        long nowNanos = System.nanoTime();
        long currentNext = nextFreeTicketNanos.getAndUpdate(old -> Math.max(old, nowNanos) + intervalNanos);
        long waitNanos = currentNext - nowNanos;
        if (waitNanos > 0) {
            try {
                TimeUnit.NANOSECONDS.sleep(waitNanos);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("RateLimiter bị ngắt khi đang sleep", e);
            }
        }
    }

    public double getPermitsPerSecond() {
        return permitsPerSecond;
    }
}
