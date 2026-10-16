package vn.eventplatform.order.client.dto;

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
public class VoucherCalculationResponse {

    private UUID promotionId;
    private String code;
    private BigDecimal discountAmount;
    private BigDecimal payableAmount;
}
