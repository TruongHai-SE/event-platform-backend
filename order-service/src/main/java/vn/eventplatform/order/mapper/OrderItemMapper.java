package vn.eventplatform.order.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import vn.eventplatform.order.domain.OrderItem;
import vn.eventplatform.order.dto.CreateOrderItemRequest;
import vn.eventplatform.order.dto.OrderItemResponse;

import java.math.BigDecimal;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderItemMapper {

    @Mapping(target = "subtotal", expression = "java(calculateSubtotal(item))")
    OrderItemResponse toResponse(OrderItem item);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    OrderItem toEntity(CreateOrderItemRequest request);

    default BigDecimal calculateSubtotal(OrderItem item) {
        if (item == null || item.getUnitPrice() == null || item.getQuantity() == null) {
            return BigDecimal.ZERO;
        }
        return item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
    }
}
