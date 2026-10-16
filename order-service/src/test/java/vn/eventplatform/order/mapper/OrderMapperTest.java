package vn.eventplatform.order.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.eventplatform.order.domain.Order;
import vn.eventplatform.order.domain.OrderItem;
import vn.eventplatform.order.domain.OrderStatus;
import vn.eventplatform.order.dto.CreateOrderItemRequest;
import vn.eventplatform.order.dto.OrderItemResponse;
import vn.eventplatform.order.dto.OrderResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import org.mapstruct.factory.Mappers;

class OrderMapperTest {

    private OrderMapper orderMapper;
    private OrderItemMapper orderItemMapper;

    @BeforeEach
    void setUp() {
        orderItemMapper = Mappers.getMapper(OrderItemMapper.class);
        orderMapper = Mappers.getMapper(OrderMapper.class);
        ReflectionTestUtils.setField(orderMapper, "orderItemMapper", orderItemMapper);
    }

    @Test
    @DisplayName("Ánh xạ từ CreateOrderItemRequest sang OrderItem entity")
    void shouldMapCreateOrderItemRequestToEntity() {
        UUID ticketTypeId = UUID.randomUUID();
        CreateOrderItemRequest request = CreateOrderItemRequest.builder()
                .ticketTypeId(ticketTypeId)
                .quantity(3)
                .unitPrice(new BigDecimal("350000"))
                .build();

        OrderItem item = orderItemMapper.toEntity(request);

        assertThat(item).isNotNull();
        assertThat(item.getTicketTypeId()).isEqualTo(ticketTypeId);
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getUnitPrice()).isEqualByComparingTo(new BigDecimal("350000"));
    }

    @Test
    @DisplayName("Ánh xạ từ Order entity kèm OrderItems sang OrderResponse")
    void shouldMapOrderEntityToOrderResponse() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        Instant now = Instant.now();

        Order order = Order.builder()
                .id(orderId)
                .orderCode("TKZ-20261013-VIP01")
                .userId(userId)
                .eventId(eventId)
                .showTimeId(showTimeId)
                .subtotal(new BigDecimal("1000000"))
                .discountAmount(new BigDecimal("100000"))
                .totalAmount(new BigDecimal("900000"))
                .status(OrderStatus.PENDING)
                .customerName("Tran Van B")
                .customerEmail("vanb@example.com")
                .customerPhone("0912345678")
                .expiresAt(now.plusSeconds(900))
                .build();

        OrderItem item = OrderItem.builder()
                .id(itemId)
                .ticketTypeId(ticketTypeId)
                .quantity(2)
                .unitPrice(new BigDecimal("500000"))
                .build();

        order.addItem(item);

        OrderResponse response = orderMapper.toResponse(order);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(orderId);
        assertThat(response.getOrderCode()).isEqualTo("TKZ-20261013-VIP01");
        assertThat(response.getUserId()).isEqualTo(userId);
        assertThat(response.getEventId()).isEqualTo(eventId);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getSubtotal()).isEqualByComparingTo(new BigDecimal("1000000"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("900000"));
        assertThat(response.getItems()).hasSize(1);

        OrderItemResponse itemResponse = response.getItems().get(0);
        assertThat(itemResponse.getId()).isEqualTo(itemId);
        assertThat(itemResponse.getTicketTypeId()).isEqualTo(ticketTypeId);
        assertThat(itemResponse.getQuantity()).isEqualTo(2);
        assertThat(itemResponse.getSubtotal()).isEqualByComparingTo(new BigDecimal("1000000"));
    }
}
