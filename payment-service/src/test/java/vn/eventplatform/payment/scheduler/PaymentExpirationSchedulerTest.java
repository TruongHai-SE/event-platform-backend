package vn.eventplatform.payment.scheduler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.eventplatform.payment.service.PaymentService;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentExpirationSchedulerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentExpirationScheduler scheduler;

    @Test
    @DisplayName("Quét và hủy các payment quá hạn thành công khi có bản ghi")
    void scanAndCancelExpiredPayments_Success() {
        when(paymentService.cancelExpiredPendingPayments()).thenReturn(3);

        assertDoesNotThrow(() -> scheduler.scanAndCancelExpiredPayments());

        verify(paymentService).cancelExpiredPendingPayments();
    }

    @Test
    @DisplayName("Quét bình thường khi không có payment quá hạn")
    void scanAndCancelExpiredPayments_WhenZeroFound() {
        when(paymentService.cancelExpiredPendingPayments()).thenReturn(0);

        assertDoesNotThrow(() -> scheduler.scanAndCancelExpiredPayments());

        verify(paymentService).cancelExpiredPendingPayments();
    }

    @Test
    @DisplayName("Bắt ngoại lệ và không làm chết tiến trình scheduler khi service ném lỗi")
    void scanAndCancelExpiredPayments_HandlesExceptionGracefully() {
        when(paymentService.cancelExpiredPendingPayments()).thenThrow(new RuntimeException("Database error"));

        assertDoesNotThrow(() -> scheduler.scanAndCancelExpiredPayments());

        verify(paymentService).cancelExpiredPendingPayments();
    }
}
