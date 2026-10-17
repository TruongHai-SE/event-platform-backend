package vn.eventplatform.promotion.dto.promotion;

import vn.eventplatform.promotion.domain.PromotionUsage;
import vn.eventplatform.promotion.enums.PromotionUsageStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Chi tiết một lần voucher được áp dụng cho đơn hàng. */
public record PromotionUsageResponse(
        UUID id,
        UUID userId,
        UUID orderId,
        BigDecimal discountAmount,
        PromotionUsageStatus status,
        LocalDateTime usedAt
) {
    public static PromotionUsageResponse from(PromotionUsage usage) {
        return new PromotionUsageResponse(
                usage.getId(),
                usage.getUserId(),
                usage.getOrderId(),
                usage.getDiscountAmount(),
                usage.getStatus(),
                usage.getUsedAt()
        );
    }
}
