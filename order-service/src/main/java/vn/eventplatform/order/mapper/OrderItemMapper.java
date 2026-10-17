package vn.eventplatform.order.mapper;

import org.springframework.stereotype.Component;
import vn.eventplatform.order.domain.OrderItem;
import vn.eventplatform.order.dto.CreateOrderItemRequest;
import vn.eventplatform.order.dto.OrderItemResponse;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class OrderItemMapper {

    public OrderItemResponse toResponse(OrderItem item) {
        if (item == null) {
            return null;
        }

        return OrderItemResponse.builder()
                .id(item.getId())
                .ticketTypeId(item.getTicketTypeId())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(calculateSubtotal(item))
                .build();
    }

    public OrderItem toEntity(CreateOrderItemRequest request) {
        if (request == null) {
            return null;
        }

        return OrderItem.builder()
                .ticketTypeId(request.getTicketTypeId())
                .quantity(request.getQuantity())
                .unitPrice(request.getUnitPrice())
                .createdAt(Instant.now())
                .build();
    }

    public BigDecimal calculateSubtotal(OrderItem item) {
        if (item == null || item.getUnitPrice() == null || item.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        return item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}
