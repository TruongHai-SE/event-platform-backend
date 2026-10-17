package vn.eventplatform.order.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.eventplatform.order.service.OrderService;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderExpirationScheduler scheduler;

    @Test
    @DisplayName("Scheduler: Quét và giải phóng đơn hàng hết hạn thành công")
    void shouldInvokeReleaseExpiredOrders() {
        when(orderService.releaseExpiredOrders()).thenReturn(3);

        scheduler.scanAndReleaseExpiredOrders();

        verify(orderService, times(1)).releaseExpiredOrders();
    }

    @Test
    @DisplayName("Scheduler: Bắt ngoại lệ và không làm sập scheduler khi orderService gặp lỗi")
    void shouldHandleExceptionGracefully() {
        when(orderService.releaseExpiredOrders()).thenThrow(new RuntimeException("Database error"));

        // Không ném ngoại lệ ra ngoài
        scheduler.scanAndReleaseExpiredOrders();

        verify(orderService, times(1)).releaseExpiredOrders();
    }
}
