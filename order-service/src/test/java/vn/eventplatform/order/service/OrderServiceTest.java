package vn.eventplatform.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.order.client.EventServiceClient;
import vn.eventplatform.order.client.EventServiceFallback;
import vn.eventplatform.order.client.PromotionServiceClient;
import vn.eventplatform.order.client.dto.CheckAndReserveInventoryResponse;
import vn.eventplatform.order.client.dto.TicketTypeInventoryDto;
import vn.eventplatform.order.client.dto.VoucherApplyRequest;
import vn.eventplatform.order.client.dto.VoucherCalculationResponse;
import vn.eventplatform.order.client.dto.VoucherReleaseRequest;
import vn.eventplatform.order.client.dto.VoucherValidationRequest;
import vn.eventplatform.order.client.dto.ReleaseInventoryRequest;
import vn.eventplatform.order.domain.Order;
import vn.eventplatform.order.domain.OrderItem;
import vn.eventplatform.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import vn.eventplatform.order.dto.CreateOrderItemRequest;
import vn.eventplatform.order.dto.CreateOrderRequest;
import vn.eventplatform.order.dto.OrderCountdownResponse;
import vn.eventplatform.order.dto.OrderFilterRequest;
import vn.eventplatform.order.dto.OrderResponse;
import vn.eventplatform.order.mapper.OrderMapper;
import vn.eventplatform.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private EventServiceClient eventServiceClient;

    @Mock
    private PromotionServiceClient promotionServiceClient;

    @Mock
    private vn.eventplatform.order.lock.InventoryLockService inventoryLockService;

    private final org.springframework.transaction.support.TransactionOperations transactionOperations =
            new org.springframework.transaction.support.TransactionOperations() {
                @Override
                public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                    return action.doInTransaction(new org.springframework.transaction.support.SimpleTransactionStatus());
                }
            };

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        lenient().when(inventoryLockService.executeWithLock(any(), any(), any(java.util.function.Supplier.class))).thenAnswer(invocation -> {
            java.util.function.Supplier<?> action = invocation.getArgument(2);
            return action.get();
        });

        lenient().doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(inventoryLockService).executeWithLock(any(), any(), any(Runnable.class));

        orderService = new OrderServiceImpl(orderRepository, orderMapper, eventServiceClient, promotionServiceClient, inventoryLockService, transactionOperations);
        ReflectionTestUtils.setField(orderService, "internalSecret", "test-internal-secret");
        lenient().when(orderRepository.updateOrderStatusIfMatch(any(), any(), any(), any())).thenReturn(1);
    }

    @Test
    @DisplayName("Tạo đơn hàng thành công: Kiểm tra tồn kho, snapshot subtotal và snapshot total_amount thực trả")
    void shouldCreateOrderWithSnapshotPricesAndInventoryReservation() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId1 = UUID.randomUUID();
        UUID ticketTypeId2 = UUID.randomUUID();

        // Client chỉ gửi ticketTypeId và quantity (unitPrice nếu có từ client cũng bị override bởi snapshot)
        CreateOrderItemRequest item1 = CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId1)
                .quantity(2)
                .build();

        CreateOrderItemRequest item2 = CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId2)
                .quantity(1)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(eventId)
                .showTimeId(showTimeId)
                .customerName("Nguyen Van A")
                .customerEmail("vana@example.com")
                .customerPhone("0987654321")
                .discountAmount(new BigDecimal("100000"))
                .items(List.of(item1, item2))
                .build();

        // Giả lập Event Service trả về snapshot đơn giá chính thức: Vé 1 = 500,000, Vé 2 = 200,000
        TicketTypeInventoryDto inventory1 = TicketTypeInventoryDto.builder()
                .ticketTypeId(ticketTypeId1)
                .ticketTypeName("Vé VIP")
                .price(new BigDecimal("500000"))
                .maxPerOrder(10)
                .availableQuantity(20)
                .build();

        TicketTypeInventoryDto inventory2 = TicketTypeInventoryDto.builder()
                .ticketTypeId(ticketTypeId2)
                .ticketTypeName("Vé Thường")
                .price(new BigDecimal("200000"))
                .maxPerOrder(10)
                .availableQuantity(50)
                .build();

        CheckAndReserveInventoryResponse reserveResponse = CheckAndReserveInventoryResponse.builder()
                .success(true)
                .reservedItems(List.of(inventory1, inventory2))
                .build();

        when(eventServiceClient.checkAndReserveInventory(any(), any()))
                .thenReturn(ApiResponse.ok(reserveResponse));

        when(orderRepository.existsByOrderCode(any())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse expectedResponse = OrderResponse.builder()
                .orderCode("TKZ-20261013-ABC12")
                .subtotal(new BigDecimal("1200000"))
                .discountAmount(new BigDecimal("100000"))
                .totalAmount(new BigDecimal("1100000"))
                .status(OrderStatus.PENDING)
                .build();
        when(orderMapper.toResponse(any(Order.class))).thenReturn(expectedResponse);

        OrderResponse response = orderService.createOrder(userId, request);

        assertThat(response).isNotNull();
        verify(eventServiceClient).checkAndReserveInventory(eq("test-internal-secret"), any());

        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getUserId()).isEqualTo(userId);
        assertThat(savedOrder.getEventId()).isEqualTo(eventId);
        assertThat(savedOrder.getShowTimeId()).isEqualTo(showTimeId);

        // Snapshot Subtotal = (2 * 500,000) + (1 * 200,000) = 1,200,000
        assertThat(savedOrder.getSubtotal()).isEqualByComparingTo(new BigDecimal("1200000"));
        // Snapshot DiscountAmount = 100,000
        assertThat(savedOrder.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("100000"));
        // Snapshot TotalAmount thực trả = 1,200,000 - 100,000 = 1,100,000
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1100000"));

        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getOrderCode()).matches("^TKZ-\\d{8}-[A-Z0-9]+$");

        // Giữ chỗ ~15 phút
        Instant now = Instant.now();
        assertThat(savedOrder.getExpiresAt()).isAfter(now.plus(14, ChronoUnit.MINUTES));
        assertThat(savedOrder.getExpiresAt()).isBefore(now.plus(16, ChronoUnit.MINUTES));

        // Kiểm tra OrderItems đã snapshot đúng đơn giá
        assertThat(savedOrder.getItems()).hasSize(2);
        assertThat(savedOrder.getItems().get(0).getUnitPrice()).isEqualByComparingTo(new BigDecimal("500000"));
        assertThat(savedOrder.getItems().get(1).getUnitPrice()).isEqualByComparingTo(new BigDecimal("200000"));
    }

    @Test
    @DisplayName("Ném ngoại lệ khi tồn kho không đủ (Event Service báo lỗi)")
    void shouldThrowExceptionWhenInventoryInsufficient() {
        UUID userId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        CreateOrderItemRequest item = CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId)
                .quantity(10)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(UUID.randomUUID())
                .showTimeId(UUID.randomUUID())
                .items(List.of(item))
                .build();

        when(eventServiceClient.checkAndReserveInventory(any(), any()))
                .thenThrow(new AppException(ErrorCode.BAD_REQUEST, "Hạng vé không đủ số lượng vé khả dụng (còn lại: 2, yêu cầu: 10)"));

        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không đủ số lượng vé khả dụng");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Tích hợp thực tế với EventServiceFallback: Kiểm tra tồn kho & trừ kho thành công")
    void shouldWorkWithEventServiceFallback() {
        EventServiceFallback fallback = new EventServiceFallback();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        // Seed tồn kho 5 vé giá 300,000
        fallback.seedInventory(showTimeId, ticketTypeId, "Vé VIP", new BigDecimal("300000"), 5, 10);

        OrderServiceImpl serviceWithFallback = new OrderServiceImpl(orderRepository, orderMapper, fallback, promotionServiceClient, inventoryLockService, transactionOperations);
        ReflectionTestUtils.setField(serviceWithFallback, "internalSecret", "secret");

        UUID userId = UUID.randomUUID();
        CreateOrderItemRequest item = CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId)
                .quantity(3)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .customerName("Le Van C")
                .customerEmail("vanc@example.com")
                .items(List.of(item))
                .discountAmount(new BigDecimal("50000"))
                .build();

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toResponse(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return OrderResponse.builder()
                    .subtotal(o.getSubtotal())
                    .discountAmount(o.getDiscountAmount())
                    .totalAmount(o.getTotalAmount())
                    .status(o.getStatus())
                    .build();
        });

        OrderResponse res = serviceWithFallback.createOrder(userId, request);

        // Subtotal = 3 * 300,000 = 900,000
        // Discount = 50,000
        // TotalAmount = 850,000
        assertThat(res.getSubtotal()).isEqualByComparingTo(new BigDecimal("900000"));
        assertThat(res.getTotalAmount()).isEqualByComparingTo(new BigDecimal("850000"));

        // Lần mua tiếp theo yêu cầu 3 vé nữa -> tồn kho chỉ còn 2 vé khả dụng (5 - 3 = 2) -> phải ném ngoại lệ
        assertThatThrownBy(() -> serviceWithFallback.createOrder(userId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không đủ số lượng vé khả dụng");
    }

    @Test
    @DisplayName("Ném ngoại lệ khi danh sách items rỗng")
    void shouldThrowWhenItemsEmpty() {
        UUID userId = UUID.randomUUID();
        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(UUID.randomUUID())
                .showTimeId(UUID.randomUUID())
                .items(Collections.emptyList())
                .build();

        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Đơn hàng phải chứa ít nhất 1 hạng vé");
    }

    @Test
    @DisplayName("Giới hạn giảm giá tối đa không vượt quá subtotal")
    void shouldCapDiscountAmountAtSubtotal() {
        UUID userId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        CreateOrderItemRequest item = CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId)
                .quantity(1)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(UUID.randomUUID())
                .showTimeId(UUID.randomUUID())
                .discountAmount(new BigDecimal("500000")) // giảm giá lớn hơn subtotal
                .items(List.of(item))
                .build();

        TicketTypeInventoryDto inv = TicketTypeInventoryDto.builder()
                .ticketTypeId(ticketTypeId)
                .ticketTypeName("Vé Thường")
                .price(new BigDecimal("100000"))
                .maxPerOrder(10)
                .availableQuantity(50)
                .build();

        when(eventServiceClient.checkAndReserveInventory(any(), any()))
                .thenReturn(ApiResponse.ok(CheckAndReserveInventoryResponse.builder()
                        .success(true)
                        .reservedItems(List.of(inv))
                        .build()));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.createOrder(userId, request);

        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getSubtotal()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(savedOrder.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Từ chối truy cập khi người dùng khác xem chi tiết đơn hàng")
    void shouldThrowAccessDeniedWhenNotOwnerNorAdmin() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .userId(ownerId)
                .build();

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(orderId, otherUserId, false))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("Cho phép xem chi tiết đơn hàng nếu là Admin")
    void shouldAllowAdminToViewOrder() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .userId(ownerId)
                .build();

        OrderResponse response = OrderResponse.builder().id(orderId).userId(ownerId).build();

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response);

        OrderResponse result = orderService.getOrderById(orderId, adminId, true);
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("Chống oversell & lỗi dữ liệu: Từ chối đơn hàng chứa hạng vé bị trùng lặp")
    void shouldThrowBadRequestWhenDuplicateTicketTypesInOrderRequest() {
        UUID userId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID duplicateTicketTypeId = UUID.randomUUID();

        CreateOrderItemRequest item1 = CreateOrderItemRequest.builder()
                .ticketTypeId(duplicateTicketTypeId)
                .quantity(2)
                .build();

        CreateOrderItemRequest item2 = CreateOrderItemRequest.builder()
                .ticketTypeId(duplicateTicketTypeId)
                .quantity(1)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .customerName("Nguyen Van A")
                .customerEmail("vana@example.com")
                .items(List.of(item1, item2))
                .build();

        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("không được chứa các hạng vé trùng lặp");

        verify(orderRepository, never()).save(any());
        verify(inventoryLockService, never()).executeWithLock(any(), any(), any(java.util.function.Supplier.class));
    }

    @Test
    @DisplayName("Redis Lock: Xác nhận executeWithLock được gọi đúng showTimeId và danh sách ticketTypeIds")
    void shouldInvokeInventoryLockServiceWithExactShowTimeAndTicketTypes() {
        UUID userId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId1 = UUID.randomUUID();
        UUID ticketTypeId2 = UUID.randomUUID();

        CreateOrderItemRequest item1 = CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId1)
                .quantity(2)
                .build();

        CreateOrderItemRequest item2 = CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId2)
                .quantity(1)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .customerName("Nguyen Van B")
                .customerEmail("vanb@example.com")
                .items(List.of(item1, item2))
                .build();

        // Mock eventServiceClient trả về inventory hợp lệ
        TicketTypeInventoryDto inv1 = TicketTypeInventoryDto.builder()
                .ticketTypeId(ticketTypeId1)
                .ticketTypeName("Vé Standard")
                .price(new BigDecimal("150000"))
                .maxPerOrder(10)
                .availableQuantity(50)
                .build();

        TicketTypeInventoryDto inv2 = TicketTypeInventoryDto.builder()
                .ticketTypeId(ticketTypeId2)
                .ticketTypeName("Vé VIP")
                .price(new BigDecimal("350000"))
                .maxPerOrder(5)
                .availableQuantity(20)
                .build();

        CheckAndReserveInventoryResponse mockReserveResponse = CheckAndReserveInventoryResponse.builder()
                .success(true)
                .reservedItems(List.of(inv1, inv2))
                .build();

        when(eventServiceClient.checkAndReserveInventory(any(), any()))
                .thenReturn(ApiResponse.ok(mockReserveResponse));

        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toResponse(any())).thenReturn(OrderResponse.builder().id(UUID.randomUUID()).build());

        OrderResponse res = orderService.createOrder(userId, request);

        assertThat(res).isNotNull();
        // Kiểm tra executeWithLock được gọi với showTimeId và các ticketTypeIds
        verify(inventoryLockService).executeWithLock(eq(showTimeId), eq(List.of(ticketTypeId1, ticketTypeId2)), any(java.util.function.Supplier.class));
    }

    @Test
    @DisplayName("Gọi Promotion Service validate voucher và lưu snapshot discount_amount, applied_promotion_id vào Order")
    void shouldValidatePromotionCodeAndSnapshotDiscount() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID promoId = UUID.randomUUID();
        String promoCode = "SUMMER2026";

        CreateOrderItemRequest item = CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId)
                .quantity(2)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(eventId)
                .showTimeId(showTimeId)
                .customerName("Voucher User")
                .customerEmail("voucher@example.com")
                .promotionCode(promoCode)
                .items(List.of(item))
                .build();

        // Subtotal = 2 * 300,000 = 600,000
        TicketTypeInventoryDto inv = TicketTypeInventoryDto.builder()
                .ticketTypeId(ticketTypeId)
                .ticketTypeName("Vé Standard")
                .price(new BigDecimal("300000"))
                .maxPerOrder(10)
                .availableQuantity(50)
                .build();

        when(eventServiceClient.checkAndReserveInventory(any(), any()))
                .thenReturn(ApiResponse.ok(CheckAndReserveInventoryResponse.builder()
                        .success(true)
                        .reservedItems(List.of(inv))
                        .build()));

        VoucherCalculationResponse voucherCalc = VoucherCalculationResponse.builder()
                .promotionId(promoId)
                .code(promoCode)
                .discountAmount(new BigDecimal("100000"))
                .payableAmount(new BigDecimal("500000"))
                .build();

        when(promotionServiceClient.validateVoucher(eq("test-internal-secret"), any(VoucherValidationRequest.class)))
                .thenReturn(ApiResponse.ok(voucherCalc));

        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(orderMapper.toResponse(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            return OrderResponse.builder()
                    .subtotal(o.getSubtotal())
                    .discountAmount(o.getDiscountAmount())
                    .totalAmount(o.getTotalAmount())
                    .build();
        });

        OrderResponse response = orderService.createOrder(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("600000"));
        assertThat(response.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("500000"));

        verify(promotionServiceClient).validateVoucher(eq("test-internal-secret"), argThat(req ->
                req.getUserId().equals(userId)
                        && req.getEventId().equals(eventId)
                        && req.getCode().equals(promoCode)
                        && req.getOrderAmount().compareTo(new BigDecimal("600000")) == 0
        ));

        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getPromotionId()).isEqualTo(promoId);
        assertThat(savedOrder.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(new BigDecimal("500000"));
    }

    @Test
    @DisplayName("Ném ngoại lệ khi Promotion Service từ chối voucher hoặc trả về lỗi")
    void shouldThrowWhenPromotionServiceRejectsVoucher() {
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        CreateOrderItemRequest item = CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId)
                .quantity(1)
                .build();

        CreateOrderRequest request = CreateOrderRequest.builder()
                .eventId(eventId)
                .showTimeId(showTimeId)
                .customerName("Voucher User")
                .customerEmail("voucher@example.com")
                .promotionCode("EXPIRED_CODE")
                .items(List.of(item))
                .build();

        TicketTypeInventoryDto inv = TicketTypeInventoryDto.builder()
                .ticketTypeId(ticketTypeId)
                .ticketTypeName("Vé Standard")
                .price(new BigDecimal("200000"))
                .maxPerOrder(10)
                .availableQuantity(50)
                .build();

        when(eventServiceClient.checkAndReserveInventory(any(), any()))
                .thenReturn(ApiResponse.ok(CheckAndReserveInventoryResponse.builder()
                        .success(true)
                        .reservedItems(List.of(inv))
                        .build()));

        when(promotionServiceClient.validateVoucher(eq("test-internal-secret"), any(VoucherValidationRequest.class)))
                .thenReturn(ApiResponse.error("ERR_BAD_REQUEST", "Mã giảm giá đã hết lượt"));

        assertThatThrownBy(() -> orderService.createOrder(userId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Mã giảm giá không hợp lệ hoặc không đủ điều kiện áp dụng");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Countdown: Trả về thời gian còn lại chính xác khi đơn hàng đang PENDING")
    void shouldReturnCountdownWithRemainingSecondsWhenOrderIsPending() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant futureExpiresAt = Instant.now().plusSeconds(600);

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-20261017-TEST1")
                .userId(userId)
                .status(OrderStatus.PENDING)
                .expiresAt(futureExpiresAt)
                .build();

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        OrderCountdownResponse countdown = orderService.getOrderCountdown(orderId, userId, false);

        assertThat(countdown).isNotNull();
        assertThat(countdown.getOrderId()).isEqualTo(orderId);
        assertThat(countdown.getOrderCode()).isEqualTo("TKZ-20261017-TEST1");
        assertThat(countdown.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(countdown.getIsExpired()).isFalse();
        assertThat(countdown.getRemainingSeconds()).isBetween(580L, 600L);
        assertThat(countdown.getCurrentTime()).isNotNull();

        verify(eventServiceClient, never()).releaseInventory(any(), any());
    }

    @Test
    @DisplayName("Countdown: Tự động hết hạn và giải phóng tồn kho khi kiểm tra countdown sau khi expires_at đã qua")
    void shouldExpireOrderAndReleaseInventoryWhenCountdownCheckedAfterExpiration() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        Instant pastExpiresAt = Instant.now().minusSeconds(30);

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-20261017-EXP01")
                .userId(userId)
                .showTimeId(showTimeId)
                .status(OrderStatus.PENDING)
                .expiresAt(pastExpiresAt)
                .build();

        OrderItem item = OrderItem.builder()
                .ticketTypeId(ticketTypeId)
                .quantity(3)
                .unitPrice(new BigDecimal("250000"))
                .build();
        order.addItem(item);

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        OrderCountdownResponse countdown = orderService.getOrderCountdown(orderId, userId, false);

        assertThat(countdown).isNotNull();
        assertThat(countdown.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(countdown.getIsExpired()).isTrue();
        assertThat(countdown.getRemainingSeconds()).isZero();

        ArgumentCaptor<ReleaseInventoryRequest> releaseCaptor = ArgumentCaptor.forClass(ReleaseInventoryRequest.class);
        verify(eventServiceClient).releaseInventory(eq("test-internal-secret"), releaseCaptor.capture());

        ReleaseInventoryRequest capturedReq = releaseCaptor.getValue();
        assertThat(capturedReq.getShowTimeId()).isEqualTo(showTimeId);
        assertThat(capturedReq.getItems()).hasSize(1);
        assertThat(capturedReq.getItems().get(0).getTicketTypeId()).isEqualTo(ticketTypeId);
        assertThat(capturedReq.getItems().get(0).getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("Xem chi tiết đơn hàng: Tự động chuyển EXPIRED và giải phóng tồn kho nếu đã quá thời hạn giữ chỗ")
    void shouldExpireOrderAndReleaseInventoryWhenOrderQueriedByIdAfterExpiration() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        Instant pastExpiresAt = Instant.now().minusSeconds(10);

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-20261017-EXP02")
                .userId(userId)
                .showTimeId(showTimeId)
                .status(OrderStatus.PENDING)
                .expiresAt(pastExpiresAt)
                .build();

        OrderItem item = OrderItem.builder()
                .ticketTypeId(ticketTypeId)
                .quantity(1)
                .unitPrice(new BigDecimal("100000"))
                .build();
        order.addItem(item);

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return OrderResponse.builder()
                    .id(o.getId())
                    .status(o.getStatus())
                    .build();
        });

        OrderResponse response = orderService.getOrderById(orderId, userId, false);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(eventServiceClient).releaseInventory(eq("test-internal-secret"), any());
    }

    @Test
    @DisplayName("Batch Sweeper: Quét và giải phóng tồn kho cho tất cả đơn hàng đã hết hạn giữ chỗ")
    void shouldReleaseExpiredOrdersInBatch() {
        UUID orderId1 = UUID.randomUUID();
        UUID orderId2 = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        Instant pastExpiresAt = Instant.now().minusSeconds(60);

        Order order1 = Order.builder()
                .id(orderId1)
                .orderCode("TKZ-BATCH-01")
                .userId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .status(OrderStatus.PENDING)
                .expiresAt(pastExpiresAt)
                .build();
        order1.addItem(OrderItem.builder().ticketTypeId(ticketTypeId).quantity(2).unitPrice(new BigDecimal("150000")).build());

        Order order2 = Order.builder()
                .id(orderId2)
                .orderCode("TKZ-BATCH-02")
                .userId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .status(OrderStatus.PENDING)
                .expiresAt(pastExpiresAt)
                .build();
        order2.addItem(OrderItem.builder().ticketTypeId(ticketTypeId).quantity(1).unitPrice(new BigDecimal("150000")).build());

        when(orderRepository.findByStatusAndExpiresAtBeforeWithItems(eq(OrderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(order1, order2));

        int releasedCount = orderService.releaseExpiredOrders();

        assertThat(releasedCount).isEqualTo(2);
        assertThat(order1.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(eventServiceClient, times(2)).releaseInventory(eq("test-internal-secret"), any());
    }

    @Test
    @DisplayName("Hủy đơn hàng: Người dùng hủy đơn PENDING thành công và giải phóng tồn kho vé")
    void shouldCancelPendingOrderAndReleaseInventory() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-CANCEL-01")
                .userId(userId)
                .showTimeId(showTimeId)
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(500))
                .build();
        order.addItem(OrderItem.builder().ticketTypeId(ticketTypeId).quantity(2).unitPrice(new BigDecimal("200000")).build());

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toResponse(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return OrderResponse.builder()
                    .id(o.getId())
                    .status(o.getStatus())
                    .build();
        });

        OrderResponse response = orderService.cancelOrder(orderId, userId, false);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventServiceClient).releaseInventory(eq("test-internal-secret"), any());
    }

    @Test
    @DisplayName("Hủy đơn hàng: Quăng ngoại lệ nếu đơn hàng không ở trạng thái PENDING")
    void shouldThrowBadRequestWhenCancellingNonPendingOrder() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-PAID-01")
                .userId(userId)
                .status(OrderStatus.PAID)
                .expiresAt(Instant.now().plusSeconds(500))
                .build();

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(orderId, userId, false))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Chỉ có thể hủy đơn hàng khi đang ở trạng thái chờ thanh toán");

        verify(eventServiceClient, never()).releaseInventory(any(), any());
    }

    @Test
    @DisplayName("Hết hạn đơn hàng: Giải phóng voucher và tồn kho khi quét scheduler đơn có voucher")
    void shouldReleaseVoucherWhenOrderExpiresByScheduler() {
        UUID orderId = UUID.randomUUID();
        UUID promoId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-EXP-VOUCHER")
                .userId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .promotionId(promoId)
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().minusSeconds(100))
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .ticketTypeId(ticketTypeId)
                .quantity(2)
                .unitPrice(new BigDecimal("200000"))
                .build();
        order.setItems(List.of(item));

        when(orderRepository.findByStatusAndExpiresAtBeforeWithItems(eq(OrderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(order));
        when(eventServiceClient.releaseInventory(eq("test-internal-secret"), any())).thenReturn(ApiResponse.ok(null));
        when(promotionServiceClient.releaseVoucher(eq("test-internal-secret"), any(VoucherReleaseRequest.class)))
                .thenReturn(ApiResponse.ok(null));

        int releasedCount = orderService.releaseExpiredOrders();

        assertThat(releasedCount).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        verify(promotionServiceClient).releaseVoucher(eq("test-internal-secret"), argThat(req ->
                req.getOrderId().equals(orderId) && "EXPIRED".equals(req.getReason())));
        verify(eventServiceClient).releaseInventory(eq("test-internal-secret"), any());
    }

    @Test
    @DisplayName("Hết hạn đơn hàng: Không gọi releaseVoucher nếu đơn hàng không dùng mã giảm giá")
    void shouldNotReleaseVoucherWhenExpiredOrderHasNoPromotion() {
        UUID orderId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-EXP-NO-VOUCHER")
                .userId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .promotionId(null)
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().minusSeconds(50))
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .ticketTypeId(ticketTypeId)
                .quantity(1)
                .unitPrice(new BigDecimal("150000"))
                .build();
        order.setItems(List.of(item));

        when(orderRepository.findByStatusAndExpiresAtBeforeWithItems(eq(OrderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(order));
        when(eventServiceClient.releaseInventory(eq("test-internal-secret"), any())).thenReturn(ApiResponse.ok(null));

        int releasedCount = orderService.releaseExpiredOrders();

        assertThat(releasedCount).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        verify(promotionServiceClient, never()).releaseVoucher(any(), any());
        verify(eventServiceClient).releaseInventory(eq("test-internal-secret"), any());
    }

    @Test
    @DisplayName("Hủy đơn hàng: Giải phóng voucher với lý do REVOKED khi người dùng chủ động hủy đơn có voucher")
    void shouldReleaseVoucherWhenUserActivelyCancelsOrderWithVoucher() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID promoId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-CANCEL-VOUCHER")
                .userId(userId)
                .showTimeId(showTimeId)
                .promotionId(promoId)
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(500))
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .ticketTypeId(ticketTypeId)
                .quantity(1)
                .unitPrice(new BigDecimal("100000"))
                .build();
        order.setItems(List.of(item));

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(eventServiceClient.releaseInventory(eq("test-internal-secret"), any())).thenReturn(ApiResponse.ok(null));
        when(promotionServiceClient.releaseVoucher(eq("test-internal-secret"), any(VoucherReleaseRequest.class)))
                .thenReturn(ApiResponse.ok(null));
        when(orderMapper.toResponse(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            return OrderResponse.builder()
                    .id(o.getId())
                    .status(o.getStatus())
                    .build();
        });

        OrderResponse response = orderService.cancelOrder(orderId, userId, false);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        verify(promotionServiceClient).releaseVoucher(eq("test-internal-secret"), argThat(req ->
                req.getOrderId().equals(orderId) && "REVOKED".equals(req.getReason())));
        verify(eventServiceClient).releaseInventory(eq("test-internal-secret"), any());
    }

    @Test
    @DisplayName("Hết hạn đơn hàng: Chịu lỗi khi promotion-service gặp sự cố mà không làm gián đoạn việc quét hết hạn")
    void shouldToleratePromotionServiceFailureWhenOrderExpires() {
        UUID orderId = UUID.randomUUID();
        UUID promoId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-EXP-FAILSAFE")
                .userId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .promotionId(promoId)
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().minusSeconds(100))
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .ticketTypeId(ticketTypeId)
                .quantity(1)
                .unitPrice(new BigDecimal("100000"))
                .build();
        order.setItems(List.of(item));

        when(orderRepository.findByStatusAndExpiresAtBeforeWithItems(eq(OrderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(order));
        when(eventServiceClient.releaseInventory(eq("test-internal-secret"), any())).thenReturn(ApiResponse.ok(null));
        when(promotionServiceClient.releaseVoucher(eq("test-internal-secret"), any(VoucherReleaseRequest.class)))
                .thenThrow(new RuntimeException("promotion-service is down"));

        int releasedCount = orderService.releaseExpiredOrders();

        assertThat(releasedCount).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(eventServiceClient).releaseInventory(eq("test-internal-secret"), any());
    }

    @Test
    @DisplayName("Idempotency: Không gọi giải phóng tồn kho hay voucher nếu trạng thái đơn hàng trong DB không còn là PENDING")
    void shouldBeStrictlyIdempotentWhenOrderStatusNoLongerPendingInDb() {
        UUID orderId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-EXP-ALREADY-CHANGED")
                .userId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .promotionId(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().minusSeconds(100))
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .ticketTypeId(ticketTypeId)
                .quantity(2)
                .unitPrice(new BigDecimal("100000"))
                .build();
        order.setItems(List.of(item));

        when(orderRepository.findByStatusAndExpiresAtBeforeWithItems(eq(OrderStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(order));
        // Giả lập luồng khác hoặc instance khác đã update trạng thái trước đó (rows = 0)
        when(orderRepository.updateOrderStatusIfMatch(eq(orderId), eq(OrderStatus.EXPIRED), eq(OrderStatus.PENDING), any()))
                .thenReturn(0);

        int releasedCount = orderService.releaseExpiredOrders();

        // Không đếm vào số lượng giải phóng thành công
        assertThat(releasedCount).isZero();
        // Không được gọi external service khi cập nhật trạng thái thất bại
        verify(eventServiceClient, never()).releaseInventory(any(), any());
        verify(promotionServiceClient, never()).releaseVoucher(any(), any());
    }

    @Test
    @DisplayName("Idempotency: Gọi expireOrder nhiều lần liên tiếp chỉ giải phóng tồn kho và voucher đúng 1 lần")
    void shouldBeIdempotentWhenExpireOrderCalledMultipleTimes() {
        UUID orderId = UUID.randomUUID();
        UUID promoId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-EXP-MULTI-CALL")
                .userId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .promotionId(promoId)
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().minusSeconds(50))
                .build();

        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .ticketTypeId(ticketTypeId)
                .quantity(1)
                .unitPrice(new BigDecimal("150000"))
                .build();
        order.setItems(List.of(item));

        when(eventServiceClient.releaseInventory(eq("test-internal-secret"), any())).thenReturn(ApiResponse.ok(null));
        when(promotionServiceClient.releaseVoucher(eq("test-internal-secret"), any())).thenReturn(ApiResponse.ok(null));
        when(orderRepository.updateOrderStatusIfMatch(eq(orderId), eq(OrderStatus.EXPIRED), eq(OrderStatus.PENDING), any()))
                .thenReturn(1);

        // Gọi lần 1: thành công
        orderService.expireOrder(order);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        // Gọi lần 2: order đã ở trạng thái EXPIRED, bỏ qua ngay từ bước validate
        orderService.expireOrder(order);

        // Xác nhận side-effects chỉ được gọi đúng 1 lần duy nhất
        verify(eventServiceClient, times(1)).releaseInventory(eq("test-internal-secret"), any());
        verify(promotionServiceClient, times(1)).releaseVoucher(eq("test-internal-secret"), any());
    }

    @Test
    @DisplayName("Hết hạn đơn hàng: Không xử lý hết hạn nếu thời gian giữ chỗ chưa kết thúc")
    void shouldNotExpireOrderWhenHoldTimeNotExceeded() {
        UUID orderId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-EXP-FUTURE")
                .userId(UUID.randomUUID())
                .showTimeId(showTimeId)
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(300)) // Còn 5 phút giữ chỗ
                .build();

        order.addItem(OrderItem.builder().ticketTypeId(ticketTypeId).quantity(1).unitPrice(new BigDecimal("100000")).build());

        orderService.expireOrder(order);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).updateOrderStatusIfMatch(any(), any(), any(), any());
        verify(eventServiceClient, never()).releaseInventory(any(), any());
    }

    @Test
    @DisplayName("Lấy danh sách đơn hàng: User thường luôn bị ép buộc chỉ xem đơn của chính mình (chống IDOR)")
    void shouldGetOrdersForRegularUserEnforcingOwnUserId() {
        UUID currentUserId = UUID.randomUUID();
        UUID attackerRequestedUserId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode("TKZ-ORDER-1")
                .userId(currentUserId)
                .status(OrderStatus.PENDING)
                .build();
        Page<Order> orderPage = new PageImpl<>(List.of(order), pageable, 1);

        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
        when(orderMapper.toResponse(order)).thenReturn(OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(currentUserId)
                .build());

        OrderFilterRequest filter = OrderFilterRequest.builder()
                .userId(attackerRequestedUserId) // User thường cố gắng truyền userId của người khác
                .status(OrderStatus.PENDING)
                .build();

        Page<OrderResponse> result = orderService.getOrders(filter, currentUserId, false, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(currentUserId);
        verify(orderRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Lấy danh sách đơn hàng: Admin có thể xem tất cả đơn hoặc lọc theo bất kỳ user nào")
    void shouldGetOrdersForAdminWithAllOrdersOrFilteredUser() {
        UUID adminId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode("TKZ-ORDER-ADMIN")
                .userId(targetUserId)
                .status(OrderStatus.PAID)
                .build();
        Page<Order> orderPage = new PageImpl<>(List.of(order), pageable, 1);

        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
        when(orderMapper.toResponse(order)).thenReturn(OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(targetUserId)
                .build());

        // Admin lọc theo targetUserId
        OrderFilterRequest filterWithUser = OrderFilterRequest.builder()
                .userId(targetUserId)
                .status(OrderStatus.PAID)
                .build();

        Page<OrderResponse> resultWithUser = orderService.getOrders(filterWithUser, adminId, true, pageable);
        assertThat(resultWithUser).isNotNull();
        assertThat(resultWithUser.getContent()).hasSize(1);

        // Admin không truyền userId -> lấy tất cả
        OrderFilterRequest filterAll = OrderFilterRequest.builder().build();
        Page<OrderResponse> resultAll = orderService.getOrders(filterAll, adminId, true, pageable);
        assertThat(resultAll).isNotNull();
        assertThat(resultAll.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Lấy danh sách đơn hàng của tôi: getMyOrders hỗ trợ lọc và phân trang")
    void shouldGetMyOrdersWithFilterAndPagination() {
        UUID currentUserId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 5);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode("TKZ-MY-ORDER")
                .userId(currentUserId)
                .status(OrderStatus.PAID)
                .build();
        Page<Order> orderPage = new PageImpl<>(List.of(order), pageable, 1);

        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(orderPage);
        when(orderMapper.toResponse(order)).thenReturn(OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(currentUserId)
                .status(OrderStatus.PAID)
                .build());

        OrderFilterRequest filter = OrderFilterRequest.builder()
                .status(OrderStatus.PAID)
                .keyword("MY-ORDER")
                .build();

        Page<OrderResponse> result = orderService.getMyOrders(currentUserId, filter, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getOrderCode()).isEqualTo("TKZ-MY-ORDER");
    }

    @Test
    @DisplayName("Hủy đơn hàng theo mã đơn: Thành công khi đơn hàng PENDING và người dùng là chủ đơn")
    void shouldCancelOrderByCodeSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        String orderCode = "TKZ-20261017-CANCEL01";

        Order order = Order.builder()
                .id(orderId)
                .orderCode(orderCode)
                .userId(userId)
                .showTimeId(showTimeId)
                .status(OrderStatus.PENDING)
                .build();
        order.addItem(OrderItem.builder().ticketTypeId(ticketTypeId).quantity(2).build());

        when(orderRepository.findByOrderCodeWithItems(orderCode)).thenReturn(Optional.of(order));
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderMapper.toResponse(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return OrderResponse.builder()
                    .id(o.getId())
                    .orderCode(o.getOrderCode())
                    .status(o.getStatus())
                    .build();
        });

        OrderResponse response = orderService.cancelOrderByCode(orderCode, userId, false);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventServiceClient, times(1)).releaseInventory(eq("test-internal-secret"), any());
    }

    @Test
    @DisplayName("Hủy đơn hàng theo mã đơn: Ném ACCESS_DENIED khi user không phải chủ sở hữu và không phải admin")
    void shouldThrowAccessDeniedWhenUserCancelsAnotherUsersOrderByCode() {
        UUID ownerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        String orderCode = "TKZ-STRANGER";

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode(orderCode)
                .userId(ownerId)
                .status(OrderStatus.PENDING)
                .build();

        when(orderRepository.findByOrderCodeWithItems(orderCode)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrderByCode(orderCode, strangerId, false))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @DisplayName("Hủy đơn hàng theo mã đơn: Ném BAD_REQUEST khi trạng thái đơn hàng không phải PENDING")
    void shouldThrowBadRequestWhenCancellingNonPendingOrderByCode() {
        UUID userId = UUID.randomUUID();
        String orderCode = "TKZ-PAID";

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderCode(orderCode)
                .userId(userId)
                .status(OrderStatus.PAID)
                .build();

        when(orderRepository.findByOrderCodeWithItems(orderCode)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrderByCode(orderCode, userId, false))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }
}
