package vn.eventplatform.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.order.client.EventServiceClient;
import vn.eventplatform.order.client.PaymentServiceClient;
import vn.eventplatform.order.client.PromotionServiceClient;
import vn.eventplatform.order.client.dto.*;
import vn.eventplatform.order.domain.Order;
import vn.eventplatform.order.domain.OrderItem;
import vn.eventplatform.order.domain.OrderStatus;
import vn.eventplatform.order.dto.CreateOrderItemRequest;
import vn.eventplatform.order.dto.CreateOrderRequest;
import vn.eventplatform.order.dto.OrderCountdownResponse;
import vn.eventplatform.order.dto.OrderPaymentInitResponse;
import vn.eventplatform.order.dto.OrderResponse;
import vn.eventplatform.order.dto.RetryPaymentRequest;
import org.springframework.data.jpa.domain.Specification;
import vn.eventplatform.order.lock.InventoryLockService;
import vn.eventplatform.order.mapper.OrderMapper;
import vn.eventplatform.order.repository.OrderRepository;
import vn.eventplatform.order.dto.OrderFilterRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final EventServiceClient eventServiceClient;
    private final PromotionServiceClient promotionServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final InventoryLockService inventoryLockService;
    private final org.springframework.transaction.support.TransactionOperations transactionOperations;

    @Value("${security.internal.secret}")
    private String internalSecret;

    @Value("${order.hold-minutes:15}")
    private int holdMinutes = 15;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            EventServiceClient eventServiceClient,
            PromotionServiceClient promotionServiceClient,
            PaymentServiceClient paymentServiceClient,
            InventoryLockService inventoryLockService,
            PlatformTransactionManager transactionManager
    ) {
        this(orderRepository, orderMapper, eventServiceClient, promotionServiceClient, paymentServiceClient,
                inventoryLockService, new TransactionTemplate(transactionManager));
    }

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            EventServiceClient eventServiceClient,
            PromotionServiceClient promotionServiceClient,
            InventoryLockService inventoryLockService,
            PlatformTransactionManager transactionManager
    ) {
        this(orderRepository, orderMapper, eventServiceClient, promotionServiceClient, null,
                inventoryLockService, new TransactionTemplate(transactionManager));
    }

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            EventServiceClient eventServiceClient,
            PromotionServiceClient promotionServiceClient,
            InventoryLockService inventoryLockService,
            org.springframework.transaction.support.TransactionOperations transactionOperations
    ) {
        this(orderRepository, orderMapper, eventServiceClient, promotionServiceClient, null,
                inventoryLockService, transactionOperations);
    }

    public OrderServiceImpl(
            OrderRepository orderRepository,
            OrderMapper orderMapper,
            EventServiceClient eventServiceClient,
            PromotionServiceClient promotionServiceClient,
            PaymentServiceClient paymentServiceClient,
            InventoryLockService inventoryLockService,
            org.springframework.transaction.support.TransactionOperations transactionOperations
    ) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.eventServiceClient = eventServiceClient;
        this.promotionServiceClient = promotionServiceClient;
        this.paymentServiceClient = paymentServiceClient;
        this.inventoryLockService = inventoryLockService;
        this.transactionOperations = transactionOperations;
    }

    @Override
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng phải chứa ít nhất 1 hạng vé");
        }

        // Kiểm tra chống trùng lặp hạng vé trong cùng một đơn hàng
        Set<UUID> uniqueTicketTypes = new HashSet<>();
        for (CreateOrderItemRequest item : request.getItems()) {
            if (!uniqueTicketTypes.add(item.getTicketTypeId())) {
                throw new AppException(ErrorCode.BAD_REQUEST,
                        "Danh sách vé đặt không được chứa các hạng vé trùng lặp: " + item.getTicketTypeId());
            }
        }

        List<UUID> ticketTypeIds = request.getItems().stream()
                .map(CreateOrderItemRequest::getTicketTypeId)
                .toList();

        // Bảo vệ toàn bộ quá trình kiểm tra tồn kho và tạo đơn bằng Redis Distributed Lock theo showTime + ticketTypes
        return inventoryLockService.executeWithLock(request.getShowTimeId(), ticketTypeIds, () ->
                transactionOperations.execute(status -> doCreateOrder(userId, request))
        );
    }

    private OrderResponse doCreateOrder(UUID userId, CreateOrderRequest request) {
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

        // 3. Gọi promotion-service validate và tính giảm giá, snapshot discount_amount chính thức
        BigDecimal discount = BigDecimal.ZERO;
        UUID appliedPromotionId = null;

        String promoCode = request.getPromotionCode() != null && !request.getPromotionCode().isBlank()
                ? request.getPromotionCode().trim()
                : null;

        if (promoCode != null) {
            VoucherValidationRequest voucherRequest = VoucherValidationRequest.builder()
                    .userId(userId)
                    .eventId(request.getEventId())
                    .code(promoCode)
                    .orderAmount(subtotal)
                    .build();

            ApiResponse<VoucherCalculationResponse> voucherResponse =
                    promotionServiceClient.validateVoucher(internalSecret, voucherRequest);

            if (voucherResponse == null || !voucherResponse.isSuccess() || voucherResponse.getData() == null) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Mã giảm giá không hợp lệ hoặc không đủ điều kiện áp dụng");
            }

            VoucherCalculationResponse calculation = voucherResponse.getData();
            appliedPromotionId = calculation.getPromotionId();
            discount = calculation.getDiscountAmount() != null ? calculation.getDiscountAmount() : BigDecimal.ZERO;
        } else if (request.getDiscountAmount() != null) {
            appliedPromotionId = request.getPromotionId();
            discount = request.getDiscountAmount().max(BigDecimal.ZERO);
        }

        if (discount.compareTo(subtotal) > 0) {
            discount = subtotal;
        }

        BigDecimal totalAmount = subtotal.subtract(discount);

        // 4. Sinh mã order_code chuẩn TKZ-YYYYMMDD-XXXXX
        String orderCode = generateUniqueOrderCode();

        // 5. Thiết lập thời gian giữ chỗ 15 phút (mặc định hoặc cấu hình)
        Instant expiresAt = Instant.now().plus(holdMinutes, ChronoUnit.MINUTES);

        // 6. Khởi tạo Entity Order kèm snapshot số tiền và mã giảm giá
        Order order = Order.builder()
                .orderCode(orderCode)
                .userId(userId)
                .eventId(request.getEventId())
                .showTimeId(request.getShowTimeId())
                .promotionId(appliedPromotionId)
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

        // 8. Nếu có áp dụng mã giảm giá, gọi promotion-service apply để ghi nhận lượt dùng và chốt slot voucher
        if (promoCode != null) {
            try {
                VoucherApplyRequest applyRequest = VoucherApplyRequest.builder()
                        .orderId(order.getId())
                        .userId(userId)
                        .eventId(request.getEventId())
                        .code(promoCode)
                        .orderAmount(subtotal)
                        .build();
                ApiResponse<VoucherCalculationResponse> applyResponse =
                        promotionServiceClient.applyVoucher(internalSecret, applyRequest);
                if (applyResponse != null && !applyResponse.isSuccess()) {
                    log.warn("promotion-service trả về lỗi khi áp dụng voucher cho orderCode={}: {}",
                            order.getOrderCode(), applyResponse.getMessage());
                }
            } catch (Exception e) {
                log.error("Lỗi Feign khi gọi promotion-service applyVoucher cho orderCode={}", order.getOrderCode(), e);
            }
        }

        return orderMapper.toResponse(order);
    }

    @Override
    public OrderResponse getOrderById(UUID id, UUID currentUserId, boolean isAdminOrStaff) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        validateOrderAccess(order, currentUserId, isAdminOrStaff);
        checkAndHandleExpiredOrder(order);
        return orderMapper.toResponse(order);
    }

    @Override
    public OrderResponse getOrderByCode(String orderCode, UUID currentUserId, boolean isAdminOrStaff) {
        Order order = orderRepository.findByOrderCodeWithItems(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng với mã: " + orderCode));

        validateOrderAccess(order, currentUserId, isAdminOrStaff);
        checkAndHandleExpiredOrder(order);
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(OrderFilterRequest filter, UUID currentUserId, boolean isAdminOrStaff, Pageable pageable) {
        if (filter == null) {
            filter = new OrderFilterRequest();
        }

        // Kiểm tra phân quyền:
        // - Admin/Staff: Có thể lấy tất cả đơn hàng (nếu filter.userId == null), hoặc lọc theo userId cụ thể nếu muốn.
        // - User thường: CHỈ có thể xem đơn hàng của chính mình (IDOR protection: filter.userId luôn bị ép buộc thành currentUserId).
        UUID effectiveUserId = isAdminOrStaff ? filter.getUserId() : currentUserId;

        Specification<Order> spec = OrderSpecification.filterOrders(
                effectiveUserId,
                filter.getStatus(),
                filter.getOrderCode(),
                filter.getEventId(),
                filter.getShowTimeId(),
                filter.getKeyword(),
                filter.getFromDate(),
                filter.getToDate()
        );

        Page<Order> orders = orderRepository.findAll(spec, pageable);
        return orders.map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(UUID userId, Pageable pageable) {
        return getMyOrders(userId, new OrderFilterRequest(), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(UUID userId, OrderFilterRequest filter, Pageable pageable) {
        if (filter == null) {
            filter = new OrderFilterRequest();
        }
        filter.setUserId(userId);
        return getOrders(filter, userId, false, pageable);
    }

    @Override
    public OrderCountdownResponse getOrderCountdown(UUID id, UUID currentUserId, boolean isAdminOrStaff) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));

        validateOrderAccess(order, currentUserId, isAdminOrStaff);
        checkAndHandleExpiredOrder(order);
        return buildCountdownResponse(order);
    }

    @Override
    public OrderCountdownResponse getOrderCountdownByCode(String orderCode, UUID currentUserId, boolean isAdminOrStaff) {
        Order order = orderRepository.findByOrderCodeWithItems(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng với mã: " + orderCode));

        validateOrderAccess(order, currentUserId, isAdminOrStaff);
        checkAndHandleExpiredOrder(order);
        return buildCountdownResponse(order);
    }

    @Override
    public OrderResponse cancelOrder(UUID id, UUID currentUserId, boolean isAdminOrStaff) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));
        return cancelOrderInternal(order, currentUserId, isAdminOrStaff);
    }

    @Override
    public OrderResponse cancelOrderByCode(String orderCode, UUID currentUserId, boolean isAdminOrStaff) {
        Order order = orderRepository.findByOrderCodeWithItems(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng với mã: " + orderCode));
        return cancelOrderInternal(order, currentUserId, isAdminOrStaff);
    }

    private OrderResponse cancelOrderInternal(Order order, UUID currentUserId, boolean isAdminOrStaff) {
        validateOrderAccess(order, currentUserId, isAdminOrStaff);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ có thể hủy đơn hàng khi đang ở trạng thái chờ thanh toán (PENDING)");
        }

        List<UUID> ticketTypeIds = order.getItems().stream()
                .map(OrderItem::getTicketTypeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (ticketTypeIds.isEmpty()) {
            order.setStatus(OrderStatus.CANCELLED);
            Order saved = orderRepository.save(order);
            releaseVoucherForOrder(saved, "REVOKED");
            return orderMapper.toResponse(saved);
        }

        UUID orderId = order.getId();
        UUID showTimeId = order.getShowTimeId();

        Order cancelledOrder = inventoryLockService.executeWithLock(showTimeId, ticketTypeIds, () ->
                transactionOperations.execute(status -> {
                    Order currentOrder = orderRepository.findByIdWithItems(orderId).orElse(order);
                    if (currentOrder.getStatus() != OrderStatus.PENDING) {
                        throw new AppException(ErrorCode.BAD_REQUEST, "Trạng thái đơn hàng đã thay đổi, không thể hủy");
                    }

                    currentOrder.setStatus(OrderStatus.CANCELLED);
                    Order savedOrder = orderRepository.save(currentOrder);

                    releaseInventoryForOrder(savedOrder);
                    releaseVoucherForOrder(savedOrder, "REVOKED");
                    log.info("Đã hủy đơn hàng chủ động, giải phóng tồn kho và voucher: orderCode={}, showTimeId={}",
                            savedOrder.getOrderCode(), savedOrder.getShowTimeId());
                    return savedOrder;
                })
        );

        return orderMapper.toResponse(cancelledOrder);
    }

    @Override
    public OrderPaymentInitResponse retryPayment(
            UUID id,
            RetryPaymentRequest request,
            UUID currentUserId,
            boolean isAdminOrStaff,
            String clientIp
    ) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng"));
        return retryPaymentInternal(order, request, currentUserId, isAdminOrStaff, clientIp);
    }

    @Override
    public OrderPaymentInitResponse retryPaymentByCode(
            String orderCode,
            RetryPaymentRequest request,
            UUID currentUserId,
            boolean isAdminOrStaff,
            String clientIp
    ) {
        Order order = orderRepository.findByOrderCodeWithItems(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn hàng với mã: " + orderCode));
        return retryPaymentInternal(order, request, currentUserId, isAdminOrStaff, clientIp);
    }

    private OrderPaymentInitResponse retryPaymentInternal(
            Order order,
            RetryPaymentRequest request,
            UUID currentUserId,
            boolean isAdminOrStaff,
            String clientIp
    ) {
        validateOrderAccess(order, currentUserId, isAdminOrStaff);

        if (request == null || request.getMethod() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Phương thức thanh toán (method) không được để trống");
        }

        checkAndHandleExpiredOrder(order);

        if (order.getStatus() == OrderStatus.EXPIRED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng đã hết thời gian giữ chỗ thanh toán (15 phút)");
        }

        if (order.getStatus() == OrderStatus.PAID) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng đã được thanh toán, không thể thử lại");
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Chỉ có thể thử lại thanh toán cho đơn hàng đang ở trạng thái chờ thanh toán (PENDING)");
        }

        if (paymentServiceClient == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Dịch vụ thanh toán chưa được cấu hình");
        }

        InternalRetryPaymentRequest internalRequest = InternalRetryPaymentRequest.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .amount(order.getTotalAmount())
                .method(request.getMethod())
                .expiresAt(order.getExpiresAt())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .clientIp(clientIp)
                .build();

        ApiResponse<OrderPaymentInitResponse> response = paymentServiceClient.retryPayment(internalSecret, internalRequest);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            String errMsg = response != null && response.getMessage() != null
                    ? response.getMessage()
                    : "Không thể khởi tạo lại liên kết thanh toán cho đơn hàng này";
            throw new AppException(ErrorCode.BAD_REQUEST, errMsg);
        }

        log.info("Đã khởi tạo lại thanh toán thành công cho đơn hàng: orderCode={}, orderId={}, method={}",
                order.getOrderCode(), order.getId(), request.getMethod());
        return response.getData();
    }

    @Override
    public void expireOrder(Order order) {
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        Instant now = Instant.now();
        if (order.getExpiresAt() != null && order.getExpiresAt().isAfter(now)) {
            log.debug("Đơn hàng {} chưa quá hạn (expiresAt={}), bỏ qua expireOrder", order.getOrderCode(), order.getExpiresAt());
            return;
        }

        List<UUID> ticketTypeIds = order.getItems() != null ? order.getItems().stream()
                .map(OrderItem::getTicketTypeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList() : List.of();

        UUID orderId = order.getId();
        UUID showTimeId = order.getShowTimeId();

        Runnable expireTask = () -> {
            // Đảm bảo tính Idempotent: Dùng atomic update trong DB để kiểm tra và chuyển trạng thái PENDING -> EXPIRED.
            // Chỉ duy nhất 1 luồng thành công (rows > 0) mới được phép thực thi side-effects (giải phóng tồn kho & voucher).
            boolean updated = Boolean.TRUE.equals(transactionOperations.execute(status -> {
                int rows = orderRepository.updateOrderStatusIfMatch(
                        orderId, OrderStatus.EXPIRED, OrderStatus.PENDING, Instant.now());
                if (rows > 0) {
                    order.setStatus(OrderStatus.EXPIRED);
                    return true;
                }
                return false;
            }));

            if (updated) {
                releaseInventoryForOrder(order);
                releaseVoucherForOrder(order, "EXPIRED");
                log.info("Đã chuyển trạng thái hết hạn, giải phóng tồn kho và voucher cho đơn hàng: orderCode={}, showTimeId={}",
                        order.getOrderCode(), order.getShowTimeId());
            } else {
                log.debug("Đơn hàng {} không còn ở trạng thái PENDING hoặc đã được xử lý bởi luồng khác, bỏ qua expire",
                        order.getOrderCode());
            }
        };

        if (ticketTypeIds.isEmpty() || showTimeId == null) {
            expireTask.run();
        } else {
            inventoryLockService.executeWithLock(showTimeId, ticketTypeIds, expireTask);
        }
    }

    @Override
    public int releaseExpiredOrders() {
        Instant now = Instant.now();
        List<Order> expiredOrders = orderRepository.findByStatusAndExpiresAtBeforeWithItems(OrderStatus.PENDING, now);
        if (expiredOrders.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Order order : expiredOrders) {
            try {
                expireOrder(order);
                if (order.getStatus() == OrderStatus.EXPIRED) {
                    count++;
                }
            } catch (Exception e) {
                log.error("Lỗi khi giải phóng tồn kho cho đơn hàng hết hạn: orderCode={}", order.getOrderCode(), e);
            }
        }
        return count;
    }

    private void checkAndHandleExpiredOrder(Order order) {
        if (order.getStatus() == OrderStatus.PENDING
                && order.getExpiresAt() != null
                && order.getExpiresAt().isBefore(Instant.now())) {
            expireOrder(order);
        }
    }

    private void releaseInventoryForOrder(Order order) {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }

        List<TicketItemReservationRequest> releaseItems = order.getItems().stream()
                .map(item -> TicketItemReservationRequest.builder()
                        .ticketTypeId(item.getTicketTypeId())
                        .quantity(item.getQuantity())
                        .build())
                .toList();

        ReleaseInventoryRequest releaseRequest = ReleaseInventoryRequest.builder()
                .showTimeId(order.getShowTimeId())
                .items(releaseItems)
                .build();

        try {
            ApiResponse<Void> response = eventServiceClient.releaseInventory(internalSecret, releaseRequest);
            if (response != null && !response.isSuccess()) {
                log.warn("event-service trả về lỗi khi giải phóng tồn kho đơn hàng {}: {}",
                        order.getOrderCode(), response.getMessage());
            }
        } catch (Exception e) {
            log.error("Lỗi Feign khi gọi event-service releaseInventory cho orderCode={}", order.getOrderCode(), e);
        }
    }

    private void releaseVoucherForOrder(Order order, String reason) {
        if (order == null || order.getPromotionId() == null) {
            return;
        }

        VoucherReleaseRequest releaseRequest = VoucherReleaseRequest.builder()
                .orderId(order.getId())
                .reason(reason)
                .build();

        try {
            ApiResponse<Void> response = promotionServiceClient.releaseVoucher(internalSecret, releaseRequest);
            if (response != null && !response.isSuccess()) {
                log.warn("promotion-service trả về lỗi khi giải phóng voucher cho đơn hàng {}: {}",
                        order.getOrderCode(), response.getMessage());
            } else {
                log.info("Đã giải phóng voucher thành công cho đơn hàng: orderCode={}, orderId={}, promotionId={}, reason={}",
                        order.getOrderCode(), order.getId(), order.getPromotionId(), reason);
            }
        } catch (Exception e) {
            log.error("Lỗi Feign khi gọi promotion-service releaseVoucher cho orderCode={}", order.getOrderCode(), e);
        }
    }

    private OrderCountdownResponse buildCountdownResponse(Order order) {
        Instant now = Instant.now();
        Instant expiresAt = order.getExpiresAt();
        long remainingSeconds = 0L;
        boolean isExpired = false;

        if (order.getStatus() == OrderStatus.PENDING) {
            if (expiresAt != null && expiresAt.isAfter(now)) {
                remainingSeconds = Math.max(0, Duration.between(now, expiresAt).getSeconds());
            } else {
                isExpired = true;
            }
        } else if (order.getStatus() == OrderStatus.EXPIRED) {
            isExpired = true;
        }

        return OrderCountdownResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .expiresAt(expiresAt)
                .currentTime(now)
                .remainingSeconds(remainingSeconds)
                .isExpired(isExpired)
                .build();
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

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getInternalOrderById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng với id: " + id));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getInternalOrderByCode(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng với mã: " + orderCode));
        return orderMapper.toResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse markOrderPaid(UUID id, vn.eventplatform.order.dto.OrderMarkPaidRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy đơn hàng với id: " + id));

        if (order.getStatus() == OrderStatus.PAID) {
            log.info("Đơn hàng {} đã ở trạng thái PAID (idempotent call)", order.getOrderCode());
            return orderMapper.toResponse(order);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            log.warn("Không thể chuyển sang PAID vì đơn hàng {} đang ở trạng thái {}", order.getOrderCode(), order.getStatus());
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng không ở trạng thái PENDING");
        }

        order.setStatus(OrderStatus.PAID);
        Order savedOrder = orderRepository.save(order);
        log.info("Đơn hàng {} đã được cập nhật sang trạng thái PAID thành công", savedOrder.getOrderCode());

        return orderMapper.toResponse(savedOrder);
    }
}
