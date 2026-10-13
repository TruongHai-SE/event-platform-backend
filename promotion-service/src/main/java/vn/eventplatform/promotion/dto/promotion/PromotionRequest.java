package vn.eventplatform.promotion.dto.promotion;

import jakarta.validation.constraints.*;
import vn.eventplatform.promotion.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PromotionRequest(

        UUID eventId,

        UUID organizerId,

        @NotBlank
        @Size(max = 50)
        String code,

        @NotNull
        DiscountType discountType,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal discountValue,

        @DecimalMin("0.00")
        BigDecimal minOrderAmount,

        @DecimalMin("0.00")
        BigDecimal maxDiscountAmount,

        @Min(1)
        Integer totalUsageLimit,

        @Min(1)
        Integer maxUsagePerUser,

        Boolean isCompensation,

        LocalDateTime validFrom,

        LocalDateTime validTo,

        Boolean isActive
) {}

