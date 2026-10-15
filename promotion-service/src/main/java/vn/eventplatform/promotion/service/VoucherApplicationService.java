package vn.eventplatform.promotion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.promotion.domain.Promotion;
import vn.eventplatform.promotion.domain.PromotionUsage;
import vn.eventplatform.promotion.domain.UserPromotion;
import vn.eventplatform.promotion.dto.voucher.*;
import vn.eventplatform.promotion.enums.DiscountType;
import vn.eventplatform.promotion.enums.PromotionUsageStatus;
import vn.eventplatform.promotion.enums.UserPromotionStatus;
import vn.eventplatform.promotion.repository.PromotionRepository;
import vn.eventplatform.promotion.repository.PromotionUsageRepository;
import vn.eventplatform.promotion.repository.UserPromotionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoucherApplicationService {
    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository usageRepository;
    private final UserPromotionRepository userPromotionRepository;

    @Transactional(readOnly = true)
    public VoucherCalculationResponse validate(VoucherValidationRequest request) {
        Promotion promotion = promotionRepository.findByCode(request.code().trim())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy voucher"));
        validateRules(promotion, request.userId(), request.eventId(), request.orderAmount());
        validatePerUserLimit(promotion, request.userId());
        if (promotion.getTotalUsageLimit() != null && promotion.getUsedCount() >= promotion.getTotalUsageLimit()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Voucher đã hết lượt sử dụng");
        }
        return calculate(promotion, request.orderAmount());
    }

    @Transactional
    public VoucherCalculationResponse apply(VoucherApplyRequest request) {
        Promotion promotion = promotionRepository.findByCode(request.code().trim())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy voucher"));
        promotion = promotionRepository.findByIdForUpdate(promotion.getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy voucher"));
        if (usageRepository.existsByOrderId(request.orderId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng đã áp dụng voucher");
        }
        validateRules(promotion, request.userId(), request.eventId(), request.orderAmount());
        validatePerUserLimit(promotion, request.userId());

        UserPromotion walletVoucher = userPromotionRepository.findByUserIdAndPromotionId(request.userId(), promotion.getId()).orElse(null);
        if (walletVoucher != null && walletVoucher.getStatus() != UserPromotionStatus.RESERVED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Voucher trong ví không còn khả dụng");
        }
        if (promotionRepository.reserveUsageSlot(promotion.getId()) != 1) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Voucher đã hết lượt sử dụng");
        }

        VoucherCalculationResponse calculation = calculate(promotion, request.orderAmount());
        usageRepository.save(PromotionUsage.builder()
                .promotionId(promotion.getId()).userId(request.userId()).orderId(request.orderId())
                .discountAmount(calculation.discountAmount()).status(PromotionUsageStatus.USED)
                .usedAt(LocalDateTime.now()).build());
        if (walletVoucher != null) {
            walletVoucher.setStatus(UserPromotionStatus.COMPLETED);
            walletVoucher.setUsedAt(LocalDateTime.now());
            userPromotionRepository.save(walletVoucher);
        }
        return calculation;
    }

    @Transactional
    public void release(VoucherReleaseRequest request) {
        PromotionUsage usage = usageRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy lịch sử áp dụng voucher"));
        Promotion promotion = promotionRepository.findByIdForUpdate(usage.getPromotionId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy voucher"));
        if (!request.isAllowedReason()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Lý do giải phóng voucher không hợp lệ");
        }
        if (usage.getStatus() != PromotionUsageStatus.USED) {
            return;
        }
        if (promotionRepository.releaseUsageSlot(promotion.getId()) != 1) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể giải phóng lượt voucher");
        }
        usage.setStatus(request.reason());
        usageRepository.save(usage);
        userPromotionRepository.findByUserIdAndPromotionId(usage.getUserId(), promotion.getId()).ifPresent(walletVoucher -> {
            walletVoucher.setStatus(UserPromotionStatus.RESERVED);
            walletVoucher.setUsedAt(null);
            userPromotionRepository.save(walletVoucher);
        });
    }

    @Transactional
    public WalletVoucherResponse issue(UUID promotionId, VoucherIssueRequest request) {
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy voucher"));
        UserPromotion walletVoucher = userPromotionRepository.findByUserIdAndPromotionId(request.userId(), promotionId)
                .orElseGet(() -> userPromotionRepository.save(UserPromotion.builder().userId(request.userId())
                        .promotionId(promotionId).source(request.source().trim()).sourceBroadcastId(request.sourceBroadcastId())
                        .status(UserPromotionStatus.RESERVED).build()));
        return walletResponse(walletVoucher, promotion);
    }

    @Transactional(readOnly = true)
    public List<WalletVoucherResponse> wallet(UUID userId) {
        return userPromotionRepository.findByUserIdOrderByIssuedAtDesc(userId).stream()
                .map(item -> walletResponse(item, promotionRepository.findById(item.getPromotionId()).orElse(null)))
                .filter(java.util.Objects::nonNull).toList();
    }

    private void validateRules(Promotion promotion, UUID userId, UUID eventId, BigDecimal orderAmount) {
        LocalDateTime now = LocalDateTime.now();
        if (!Boolean.TRUE.equals(promotion.getIsActive())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Voucher không hoạt động");
        }
        if (promotion.getValidFrom() != null && now.isBefore(promotion.getValidFrom())
                || promotion.getValidTo() != null && !now.isBefore(promotion.getValidTo())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Voucher chưa có hiệu lực hoặc đã hết hạn");
        }
        if (promotion.getEventId() != null && !promotion.getEventId().equals(eventId)) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Voucher không áp dụng cho sự kiện này");
        }
        if (promotion.getMinOrderAmount() != null && orderAmount.compareTo(promotion.getMinOrderAmount()) < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng chưa đạt giá trị tối thiểu để dùng voucher");
        }
    }

    private void validatePerUserLimit(Promotion promotion, UUID userId) {
        if (promotion.getMaxUsagePerUser() != null && usageRepository.countByUserIdAndPromotionIdAndStatus(userId, promotion.getId(), PromotionUsageStatus.USED) >= promotion.getMaxUsagePerUser()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Bạn đã dùng hết số lượt voucher cho phép");
        }
    }

    private VoucherCalculationResponse calculate(Promotion promotion, BigDecimal orderAmount) {
        BigDecimal discount = promotion.getDiscountType() == DiscountType.PERCENTAGE
                ? orderAmount.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promotion.getDiscountValue();
        if (promotion.getMaxDiscountAmount() != null) {
            discount = discount.min(promotion.getMaxDiscountAmount());
        }
        discount = discount.min(orderAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new VoucherCalculationResponse(promotion.getId(), promotion.getCode(), discount, orderAmount.subtract(discount));
    }

    private WalletVoucherResponse walletResponse(UserPromotion walletVoucher, Promotion promotion) {
        if (promotion == null) return null;
        return new WalletVoucherResponse(walletVoucher.getId(), promotion.getId(), promotion.getCode(), walletVoucher.getStatus(), walletVoucher.getIssuedAt(), promotion.getValidTo());
    }
}
