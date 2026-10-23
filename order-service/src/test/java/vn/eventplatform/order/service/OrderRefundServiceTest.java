package vn.eventplatform.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.common.messaging.event.EventCancelledEventPayload;
import vn.eventplatform.order.client.EventServiceClient;
import vn.eventplatform.order.client.PaymentServiceClient;
import vn.eventplatform.order.client.PromotionServiceClient;
import vn.eventplatform.order.client.TicketServiceClient;
import vn.eventplatform.order.client.dto.InternalEventSnapshotResponse;
import vn.eventplatform.order.client.dto.InternalRefundOrderRequest;
import vn.eventplatform.order.client.dto.ReleaseInventoryRequest;
import vn.eventplatform.order.client.dto.ShowTimeResponse;
import vn.eventplatform.order.domain.Order;
import vn.eventplatform.order.domain.OrderItem;
import vn.eventplatform.order.domain.OrderStatus;
import vn.eventplatform.order.dto.OrderRefundRequest;
import vn.eventplatform.order.dto.OrderRefundResponse;
import vn.eventplatform.order.lock.InventoryLockService;
import vn.eventplatform.order.mapper.OrderMapper;
import vn.eventplatform.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderRefundServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private EventServiceClient eventServiceClient;

    @Mock
    private PromotionServiceClient promotionServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    @Mock
    private TicketServiceClient ticketServiceClient;

    @Mock
    private InventoryLockService inventoryLockService;

    private final org.springframework.transaction.support.TransactionOperations transactionOperations =
            new org.springframework.transaction.support.TransactionOperations() {
                @Override
                public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                    return action.doInTransaction(new org.springframework.transaction.support.SimpleTransactionStatus());
                }
            };

    private OrderServiceImpl orderService;

    private UUID userId;
    private UUID orderId;
    private UUID eventId;
    private UUID showTimeId;
    private UUID ticketTypeId;
    private Order paidOrder;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        showTimeId = UUID.randomUUID();
        ticketTypeId = UUID.randomUUID();

        orderService = new OrderServiceImpl(
                orderRepository,
                orderMapper,
                eventServiceClient,
                promotionServiceClient,
                paymentServiceClient,
                ticketServiceClient,
                inventoryLockService,
                transactionOperations,
                null
        );
        ReflectionTestUtils.setField(orderService, "internalSecret", "test-secret");

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .ticketTypeId(ticketTypeId)
                .quantity(2)
                .unitPrice(new BigDecimal("250000"))
                .build();

        paidOrder = Order.builder()
                .id(orderId)
                .orderCode("TKZ-20261021-00001")
                .userId(userId)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .promotionId(UUID.randomUUID())
                .status(OrderStatus.PAID)
                .subtotal(new BigDecimal("500000"))
                .totalAmount(new BigDecimal("500000"))
                .items(new ArrayList<>(List.of(item)))
                .build();
    }

    @Nested
    @DisplayName("Trường hợp 1: Khách hàng chủ động yêu cầu hoàn vé")
    class CustomerInitiatedRefundTests {

        @Test
        @DisplayName("Thất bại nếu đơn hàng không ở trạng thái PAID")
        void shouldFailIfOrderNotPaid() {
            paidOrder.setStatus(OrderStatus.PENDING);
            when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(paidOrder));

            assertThatThrownBy(() -> orderService.requestRefund(orderId, new OrderRefundRequest("Bận việc"), userId, false))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Chỉ có thể hoàn tiền cho đơn hàng đã thanh toán");
        }

        @Test
        @DisplayName("Thất bại nếu vé trong đơn hàng đã được check-in sử dụng")
        void shouldFailIfTicketsAlreadyUsed() {
            when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(paidOrder));
            when(ticketServiceClient.hasUsedTickets(any(), eq(orderId))).thenReturn(ApiResponse.ok(true));

            assertThatThrownBy(() -> orderService.requestRefund(orderId, new OrderRefundRequest("Bận việc"), userId, false))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("đã được sử dụng (check-in)");
        }

        @Test
        @DisplayName("Thất bại nếu sự kiện áp dụng chính sách NO_REFUND")
        void shouldFailIfEventPolicyIsNoRefund() {
            when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(paidOrder));
            when(ticketServiceClient.hasUsedTickets(any(), eq(orderId))).thenReturn(ApiResponse.ok(false));
            when(eventServiceClient.getEventSnapshot(any(), eq(eventId))).thenReturn(ApiResponse.ok(
                    InternalEventSnapshotResponse.builder()
                            .id(eventId)
                            .refundPolicy("NO_REFUND")
                            .build()
            ));

            assertThatThrownBy(() -> orderService.requestRefund(orderId, new OrderRefundRequest("Bận việc"), userId, false))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("không hỗ trợ hoàn vé");
        }

        @Test
        @DisplayName("Thất bại nếu đã quá thời hạn hoàn vé trước giờ diễn")
        void shouldFailIfRefundDeadlineExpired() {
            when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(paidOrder));
            when(ticketServiceClient.hasUsedTickets(any(), eq(orderId))).thenReturn(ApiResponse.ok(false));
            when(eventServiceClient.getEventSnapshot(any(), eq(eventId))).thenReturn(ApiResponse.ok(
                    InternalEventSnapshotResponse.builder()
                            .id(eventId)
                            .refundPolicy("ALLOW_REFUND")
                            .refundDeadlineDays(3)
                            .refundFeePercent(new BigDecimal("10.00"))
                            .build()
            ));
            // Suất diễn chỉ còn 1 ngày nữa (< 3 ngày deadline)
            when(eventServiceClient.getShowTime(any(), eq(showTimeId))).thenReturn(ApiResponse.ok(
                    ShowTimeResponse.builder()
                            .id(showTimeId)
                            .startTime(Instant.now().plus(Duration.ofDays(1)))
                            .build()
            ));

            assertThatThrownBy(() -> orderService.requestRefund(orderId, new OrderRefundRequest("Bận việc"), userId, false))
                    .isInstanceOf(AppException.class)
                    .hasMessageContaining("Đã quá thời hạn được phép hoàn vé");
        }

        @Test
        @DisplayName("Thành công: Khấu trừ phí %, hủy vé, trả tồn kho về kho vé đã bán, gọi payment-service và KHÔNG hoàn voucher")
        void shouldSucceedAndDeductFeeAndReleaseInventory() {
            when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(paidOrder));
            when(ticketServiceClient.hasUsedTickets(any(), eq(orderId))).thenReturn(ApiResponse.ok(false));
            when(eventServiceClient.getEventSnapshot(any(), eq(eventId))).thenReturn(ApiResponse.ok(
                    InternalEventSnapshotResponse.builder()
                            .id(eventId)
                            .refundPolicy("ALLOW_REFUND")
                            .refundDeadlineDays(2)
                            .refundFeePercent(new BigDecimal("10.00")) // Phí 10%
                            .build()
            ));
            // Suất diễn còn 5 ngày nữa (hợp lệ >= 2 ngày)
            when(eventServiceClient.getShowTime(any(), eq(showTimeId))).thenReturn(ApiResponse.ok(
                    ShowTimeResponse.builder()
                            .id(showTimeId)
                            .startTime(Instant.now().plus(Duration.ofDays(5)))
                            .build()
            ));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            OrderRefundResponse response = orderService.requestRefund(
                    orderId,
                    new OrderRefundRequest("Bận lịch công tác"),
                    userId,
                    false
            );

            // Kiểm tra tính toán tiền: 500.000 - 10% (50.000) = 450.000
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo(OrderStatus.REFUNDED);
            assertThat(response.getOriginalAmount()).isEqualByComparingTo("500000");
            assertThat(response.getRefundFeePercent()).isEqualByComparingTo("10.00");
            assertThat(response.getRefundFeeAmount()).isEqualByComparingTo("50000.00");
            assertThat(response.getRefundAmount()).isEqualByComparingTo("450000.00");

            // Kiểm tra gọi ticket-service hủy vé
            verify(ticketServiceClient).cancelTicketsByOrderId(any(), eq(orderId), eq("Bận lịch công tác"));

            // Kiểm tra trả tồn kho vé đã bán về kho khả dụng
            verify(eventServiceClient).releaseSoldInventory(any(), any(ReleaseInventoryRequest.class));

            // Kiểm tra gọi payment-service hoàn số tiền 450.000
            ArgumentCaptor<InternalRefundOrderRequest> refundCaptor = ArgumentCaptor.forClass(InternalRefundOrderRequest.class);
            verify(paymentServiceClient).refundByOrder(any(), refundCaptor.capture());
            assertThat(refundCaptor.getValue().getOrderId()).isEqualTo(orderId);
            assertThat(refundCaptor.getValue().getAmount()).isEqualByComparingTo("450000.00");

            // Kiểm tra KHÔNG hoàn voucher (người dùng xác nhận Case 1 không hoàn voucher)
            verify(promotionServiceClient, never()).releaseVoucher(any(), any());
        }
    }

    @Nested
    @DisplayName("Trường hợp 2: Sự kiện bị hủy bởi Ban tổ chức / Quản trị viên")
    class EventCancellationRefundTests {

        @Test
        @DisplayName("Hoàn tiền 100% không khấu trừ phí + hoàn voucher cho đơn PAID, hủy đơn PENDING")
        void shouldRefund100PercentAndReleaseVoucherForPaidOrders() {
            UUID pendingOrderId = UUID.randomUUID();
            Order pendingOrder = Order.builder()
                    .id(pendingOrderId)
                    .orderCode("TKZ-20261021-PENDING")
                    .userId(UUID.randomUUID())
                    .eventId(eventId)
                    .showTimeId(showTimeId)
                    .promotionId(UUID.randomUUID())
                    .status(OrderStatus.PENDING)
                    .totalAmount(new BigDecimal("300000"))
                    .items(List.of())
                    .build();

            when(orderRepository.findByEventIdAndStatusInWithItems(eq(eventId), any()))
                    .thenReturn(List.of(paidOrder, pendingOrder));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            EventCancelledEventPayload payload = EventCancelledEventPayload.builder()
                    .eventId(eventId)
                    .showTimeIds(List.of(showTimeId))
                    .cancellationReason("Bão lớn đổ bộ, hủy sự kiện theo chỉ thị")
                    .cancelledAt(Instant.now())
                    .build();

            orderService.processEventCancellation(payload);

            // Kiểm tra đơn PAID: chuyển sang REFUNDED
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.REFUNDED);

            // Kiểm tra gọi hủy vé cho đơn PAID
            verify(ticketServiceClient).cancelTicketsByOrderId(any(), eq(orderId), eq("Bão lớn đổ bộ, hủy sự kiện theo chỉ thị"));

            // Kiểm tra hoàn 100% (500.000) qua payment-service cho đơn PAID
            ArgumentCaptor<InternalRefundOrderRequest> refundCaptor = ArgumentCaptor.forClass(InternalRefundOrderRequest.class);
            verify(paymentServiceClient).refundByOrder(any(), refundCaptor.capture());
            assertThat(refundCaptor.getValue().getOrderId()).isEqualTo(orderId);
            assertThat(refundCaptor.getValue().getAmount()).isEqualByComparingTo("500000");

            // Kiểm tra hoàn voucher cho cả đơn PAID và PENDING (2 lần gọi)
            verify(promotionServiceClient, times(2)).releaseVoucher(any(), any());

            // Kiểm tra đơn PENDING: chuyển sang CANCELLED
            assertThat(pendingOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }
}
