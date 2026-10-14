package vn.eventplatform.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.order.client.EventServiceClient;
import vn.eventplatform.order.client.dto.*;
import vn.eventplatform.order.domain.Order;
import vn.eventplatform.order.domain.OrderItem;
import vn.eventplatform.order.domain.OrderStatus;
import vn.eventplatform.order.dto.CreateOrderItemRequest;
import vn.eventplatform.order.dto.CreateOrderRequest;
import vn.eventplatform.order.dto.OrderResponse;
import vn.eventplatform.order.mapper.OrderMapper;
import vn.eventplatform.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final EventServiceClient eventServiceClient;

    @Value("${security.internal.secret}")
    private String internalSecret;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int HOLD_MINUTES = 15;

    @Override
    @Transactional
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng phải chứa ít nhất 1 hạng vé");
        }

        // 1. Kiểm tra tồn kho trước khi tạo order và tạm giữ chỗ vé (Hold 15 phút)
        List<TicketItemReservationRequest> reservationRequests = request.getItems().stream()
                .map(item -> TicketItemReservationRequest.builder()
                        .ticketTypeId(item.getTicketTypeId())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        CheckAndReserveInventoryRequest reserveRequest = CheckAndReserveInventoryRequest.builder()
                .eventId(request.getEventId())
                .showTimeId(request.getShowTimeId())
                .items(reservationRequests)
                .build();

        ApiResponse<CheckAndReserveInventoryResponse> reserveResponse =
                eventServiceClient.checkAndReserveInventory(internalSecret, reserveRequest);

        if (reserveResponse == null || !reserveResponse.isSuccess() || reserveResponse.getData() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể giữ chỗ vé cho đơn hàng này");
        }

        Map<UUID, TicketTypeInventoryDto> inventoryMap = reserveResponse.getData().getReservedItems().stream()
                .collect(Collectors.toMap(TicketTypeInventoryDto::getTicketTypeId, Function.identity(), (a, b) -> a));

        // 2. Tính snapshot subtotal từ đơn giá chính thức của từng hạng vé (không phụ thuộc giá client)
        BigDecimal subtotal = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();

        for (CreateOrderItemRequest itemReq : request.getItems()) {
            TicketTypeInventoryDto inventoryItem = inventoryMap.get(itemReq.getTicketTypeId());
            if (inventoryItem == null) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Không tìm thấy thông tin hạng vé: " + itemReq.getTicketTypeId());
            }

            // Snapshot đơn giá chính thức từ hệ thống sự kiện
            BigDecimal snapshotUnitPrice = inventoryItem.getPrice();
            BigDecimal itemTotal = snapshotUnitPrice.multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            subtotal = subtotal.add(itemTotal);

            OrderItem orderItem = OrderItem.builder()
                    .ticketTypeId(itemReq.getTicketTypeId())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(snapshotUnitPrice)
                    .build();
            orderItems.add(orderItem);
        }

        // 3. Tính snapshot discountAmount và totalAmount (tiền thực trả)
        BigDecimal discount = request.getDiscountAmount() != null && request.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                ? request.getDiscountAmount()
                : BigDecimal.ZERO;

        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }

        BigDecimal totalAmount = subtotal.subtract(discount);

        // 4. Sinh mã order_code chuẩn TKZ-YYYYMMDD-XXXXX
        String orderCode = generateUniqueOrderCode();

        // 5. Thiết lập thời gian giữ chỗ 15 phút theo schema
        Instant expiresAt = Instant.now().plus(HOLD_MINUTES, ChronoUnit.MINUTES);

        // 6. Khởi tạo Entity Order kèm snapshot số tiền
        Order order = Order.builder()
                .orderCode(orderCode)
                .userId(userId)
                .eventId(request.getEventId())
                .showTimeId(request.getShowTimeId())
                .promotionId(request.getPromotionId())
                .subtotal(subtotal)
                .discountAmount(discount)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .customerName(request.getCustomerName())
                .customerEmail(request.getCustomerEmail())
                .customerPhone(request.getCustomerPhone())
                .expiresAt(expiresAt)
                .build();

        // 7. Gán danh sách OrderItem đã snapshot đơn giá
        for (OrderItem item : orderItems) {
            order.addItem(item);
        }

        order = orderRepository.save(order);
        log.info("Tạo đơn hàng thành công: orderCode={}, userId={}, eventId={}, subtotal={}, totalAmount={}, expiresAt={}",
                order.getOrderCode(), userId, order.getEventId(), order.getSubtotal(), order.getTotalAmount(), order.getExpiresAt());

        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID id, UUID currentUserId, boolean isAdminOrStaff) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        validateOrderAccess(order, currentUserId, isAdminOrStaff);
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderByCode(String orderCode, UUID currentUserId, boolean isAdminOrStaff) {
        Order order = orderRepository.findByOrderCodeWithItems(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng với mã: " + orderCode));

        validateOrderAccess(order, currentUserId, isAdminOrStaff);
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(UUID userId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByUserId(userId, pageable);
        return orders.map(orderMapper::toResponse);
    }

    private void validateOrderAccess(Order order, UUID currentUserId, boolean isAdminOrStaff) {
        if (!isAdminOrStaff && !order.getUserId().equals(currentUserId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền truy cập đơn hàng này");
        }
    }

    private String generateUniqueOrderCode() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        for (int i = 0; i < 5; i++) {
            String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 5).toUpperCase();
            String code = "TKZ-" + datePart + "-" + randomPart;
            if (!orderRepository.existsByOrderCode(code)) {
                return code;
            }
        }
        return "TKZ-" + datePart + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
