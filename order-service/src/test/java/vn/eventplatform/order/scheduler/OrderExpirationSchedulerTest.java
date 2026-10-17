package vn.eventplatform.order.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import vn.eventplatform.order.service.OrderService;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private OrderExpirationScheduler scheduler;

    @Test
    @DisplayName("Scheduler: Quét và giải phóng đơn hàng hết hạn thành công khi lấy được Distributed Lock")
    void shouldInvokeReleaseExpiredOrdersWhenLockAcquired() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(orderService.releaseExpiredOrders()).thenReturn(3);

        scheduler.scanAndReleaseExpiredOrders();

        verify(orderService, times(1)).releaseExpiredOrders();
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("Scheduler: Bỏ qua chu kỳ quét khi instance khác đang giữ Distributed Lock (Idempotent per tick)")
    void shouldSkipScanningWhenLockNotAcquired() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        scheduler.scanAndReleaseExpiredOrders();

        verify(orderService, never()).releaseExpiredOrders();
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("Scheduler: Bắt ngoại lệ và vẫn giải phóng lock khi orderService gặp lỗi")
    void shouldHandleExceptionGracefullyAndUnlock() throws InterruptedException {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(orderService.releaseExpiredOrders()).thenThrow(new RuntimeException("Database error"));

        // Không ném ngoại lệ ra ngoài
        scheduler.scanAndReleaseExpiredOrders();

        verify(orderService, times(1)).releaseExpiredOrders();
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("Scheduler: Bắt ngoại lệ khi Redis gặp sự cố và không làm gián đoạn hệ thống")
    void shouldHandleRedisLockExceptionGracefully() {
        when(redissonClient.getLock(anyString())).thenThrow(new RuntimeException("Redis connection refused"));

        scheduler.scanAndReleaseExpiredOrders();

        verify(orderService, never()).releaseExpiredOrders();
    }
}
