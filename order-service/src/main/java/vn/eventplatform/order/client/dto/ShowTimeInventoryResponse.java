package vn.eventplatform.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShowTimeInventoryResponse {
    private UUID id;
    private UUID showTimeId;
    private UUID ticketTypeId;
    private String ticketTypeName;
    private BigDecimal price;
    private Integer totalQuantity;
    private Integer reservedQuantity;
    private Integer soldQuantity;
    private Integer availableQuantity;
}
