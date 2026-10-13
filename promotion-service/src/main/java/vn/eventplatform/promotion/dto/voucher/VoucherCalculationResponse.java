package vn.eventplatform.promotion.dto.voucher;

import java.math.BigDecimal;
import java.util.UUID;

public record VoucherCalculationResponse(
        UUID promotionId,
        String code,
        BigDecimal discountAmount,
        BigDecimal payableAmount
) {}
