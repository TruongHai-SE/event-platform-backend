package vn.eventplatform.order.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.eventplatform.order.domain.Order;
import vn.eventplatform.order.dto.OrderItemResponse;
import vn.eventplatform.order.dto.OrderResponse;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderMapper {

    private final OrderItemMapper orderItemMapper;

    public OrderResponse toResponse(Order order) {
        if (order == null) {
            return null;
        }

        List<OrderItemResponse> itemResponses = Collections.emptyList();
        if (order.getItems() != null) {
            itemResponses = order.getItems().stream()
                    .map(orderItemMapper::toResponse)
                    .toList();
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(order.getUserId())
                .eventId(order.getEventId())
                .showTimeId(order.getShowTimeId())
                .promotionId(order.getPromotionId())
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .customerPhone(order.getCustomerPhone())
                .expiresAt(order.getExpiresAt())
                .createdAt(order.getCreatedAt())
                .items(itemResponses)
                .build();
    }
}
