package vn.eventplatform.order.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.eventplatform.order.dto.CreateOrderRequest;
import vn.eventplatform.order.dto.OrderResponse;

import java.util.UUID;

public interface OrderService {

    OrderResponse createOrder(UUID userId, CreateOrderRequest request);

    OrderResponse getOrderById(UUID id, UUID currentUserId, boolean isAdminOrStaff);

    OrderResponse getOrderByCode(String orderCode, UUID currentUserId, boolean isAdminOrStaff);

    Page<OrderResponse> getMyOrders(UUID userId, Pageable pageable);
}
