package vn.eventplatform.promotion.dto.promotion;

import vn.eventplatform.promotion.domain.Promotion;
import vn.eventplatform.promotion.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record PromotionResponse(
        UUID id,
        UUID eventId,
        UUID organizerId,
        String code,
        DiscountType discountType,
        BigDecimal discountValue,
        BigDecimal minOrderAmount,
        BigDecimal maxDiscountAmount,
        Integer totalUsageLimit,
        Integer usedCount,
        Integer maxUsagePerUser,
        Boolean isCompensation,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        Boolean isActive,
        Instant  createdAt
) {
    public static PromotionResponse from(Promotion p) {
        return new PromotionResponse(
                p.getId(),
                p.getEventId(),
                p.getOrganizerId(),
                p.getCode(),
                p.getDiscountType(),
                p.getDiscountValue(),
                p.getMinOrderAmount(),
                p.getMaxDiscountAmount(),
                p.getTotalUsageLimit(),
                p.getUsedCount(),
                p.getMaxUsagePerUser(),
                p.getIsCompensation(),
                p.getValidFrom(),
                p.getValidTo(),
                p.getIsActive(),
                p.getCreatedAt()
        );
    }
}
