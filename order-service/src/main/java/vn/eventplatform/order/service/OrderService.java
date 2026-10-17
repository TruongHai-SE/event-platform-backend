package vn.eventplatform.order.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.eventplatform.order.domain.Order;
import vn.eventplatform.order.dto.CreateOrderRequest;
import vn.eventplatform.order.dto.OrderCountdownResponse;
import vn.eventplatform.order.dto.OrderFilterRequest;
import vn.eventplatform.order.dto.OrderResponse;

import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(UUID userId, CreateOrderRequest request);

    OrderResponse getOrderById(UUID id, UUID currentUserId, boolean isAdminOrStaff);

    OrderResponse getOrderByCode(String orderCode, UUID currentUserId, boolean isAdminOrStaff);

    Page<OrderResponse> getOrders(OrderFilterRequest filter, UUID currentUserId, boolean isAdminOrStaff, Pageable pageable);

    Page<OrderResponse> getMyOrders(UUID userId, Pageable pageable);

    Page<OrderResponse> getMyOrders(UUID userId, OrderFilterRequest filter, Pageable pageable);

    OrderCountdownResponse getOrderCountdown(UUID id, UUID currentUserId, boolean isAdminOrStaff);

    OrderCountdownResponse getOrderCountdownByCode(String orderCode, UUID currentUserId, boolean isAdminOrStaff);

    OrderResponse cancelOrder(UUID id, UUID currentUserId, boolean isAdminOrStaff);

    OrderResponse cancelOrderByCode(String orderCode, UUID currentUserId, boolean isAdminOrStaff);

    int releaseExpiredOrders();

    void expireOrder(Order order);
}
