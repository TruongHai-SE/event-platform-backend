package vn.eventplatform.promotion.dto.voucher;

import jakarta.validation.constraints.NotNull;
import vn.eventplatform.promotion.enums.PromotionUsageStatus;

import java.util.UUID;

public record VoucherReleaseRequest(@NotNull UUID orderId, @NotNull PromotionUsageStatus reason) {
    public boolean isAllowedReason() {
        return reason == PromotionUsageStatus.REVOKED || reason == PromotionUsageStatus.EXPIRED;
    }
}
