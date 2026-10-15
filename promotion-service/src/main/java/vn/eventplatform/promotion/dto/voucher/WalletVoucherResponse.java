package vn.eventplatform.promotion.dto.voucher;

import vn.eventplatform.promotion.enums.UserPromotionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record WalletVoucherResponse(
        UUID walletVoucherId,
        UUID promotionId,
        String code,
        UserPromotionStatus status,
        LocalDateTime issuedAt,
        LocalDateTime validTo
) {}
