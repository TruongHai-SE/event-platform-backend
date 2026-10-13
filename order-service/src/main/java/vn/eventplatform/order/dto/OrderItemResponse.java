package vn.eventplatform.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private UUID id;
    private UUID ticketTypeId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}
