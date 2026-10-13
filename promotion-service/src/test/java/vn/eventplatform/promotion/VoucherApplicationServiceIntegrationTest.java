package vn.eventplatform.promotion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.promotion.domain.Promotion;
import vn.eventplatform.promotion.dto.voucher.*;
import vn.eventplatform.promotion.enums.DiscountType;
import vn.eventplatform.promotion.enums.PromotionUsageStatus;
import vn.eventplatform.promotion.enums.UserPromotionStatus;
import vn.eventplatform.promotion.repository.PromotionRepository;
import vn.eventplatform.promotion.repository.PromotionUsageRepository;
import vn.eventplatform.promotion.repository.UserPromotionRepository;
import vn.eventplatform.promotion.service.VoucherApplicationService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class VoucherApplicationServiceIntegrationTest {
    @Autowired private VoucherApplicationService voucherService;
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private PromotionUsageRepository usageRepository;
    @Autowired private UserPromotionRepository userPromotionRepository;

    @BeforeEach
    void cleanDatabase() {
        usageRepository.deleteAll();
        userPromotionRepository.deleteAll();
        promotionRepository.deleteAll();
    }

    @Test
    void calculatesFixedAmountAndNeverDiscountsPastOrderTotal() {
        Promotion promotion = savePromotion(DiscountType.FIXED_AMOUNT, new BigDecimal("150.00"), null, null, null, null, null);

        VoucherCalculationResponse response = voucherService.validate(validation(promotion, new BigDecimal("100.00"), null));

        assertThat(response.discountAmount()).isEqualByComparingTo("100.00");
        assertThat(response.payableAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void calculatesPercentageAndCapsMaximumDiscount() {
        Promotion promotion = savePromotion(DiscountType.PERCENTAGE, new BigDecimal("25.00"), null, new BigDecimal("30.00"), null, null, null);

        VoucherCalculationResponse response = voucherService.validate(validation(promotion, new BigDecimal("200.00"), null));

        assertThat(response.discountAmount()).isEqualByComparingTo("30.00");
        assertThat(response.payableAmount()).isEqualByComparingTo("170.00");
    }

    @Test
    void validatesEventMinimumAmountAndValidityWindow() {
        UUID eligibleEvent = UUID.randomUUID();
        Promotion promotion = savePromotion(DiscountType.FIXED_AMOUNT, new BigDecimal("10"), new BigDecimal("100"), null, null,
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        promotion.setEventId(eligibleEvent);
        promotionRepository.save(promotion);

        assertThatThrownBy(() -> voucherService.validate(validation(promotion, new BigDecimal("99"), eligibleEvent)))
                .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> voucherService.validate(validation(promotion, new BigDecimal("100"), UUID.randomUUID())))
                .isInstanceOf(AppException.class);
        assertThat(voucherService.validate(validation(promotion, new BigDecimal("100"), eligibleEvent)).discountAmount())
                .isEqualByComparingTo("10.00");

        promotion.setValidTo(LocalDateTime.now().minusSeconds(1));
        promotionRepository.save(promotion);
        assertThatThrownBy(() -> voucherService.validate(validation(promotion, new BigDecimal("100"), eligibleEvent)))
                .isInstanceOf(AppException.class);
    }

    @Test
    void appliesOncePerOrderAndReleasesWhenOrderIsCancelled() {
        Promotion promotion = savePromotion(DiscountType.FIXED_AMOUNT, new BigDecimal("20"), null, null, 2, null, null);
        UUID userId = UUID.randomUUID();
        voucherService.issue(promotion.getId(), new VoucherIssueRequest(userId, "ADMIN", null));
        assertThat(voucherService.wallet(userId)).hasSize(1);
        UUID orderId = UUID.randomUUID();

        voucherService.apply(apply(promotion, userId, orderId));
        assertThat(usageRepository.findByOrderId(orderId)).isPresent();
        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isEqualTo(1);
        assertThat(userPromotionRepository.findByUserIdAndPromotionId(userId, promotion.getId()).orElseThrow().getStatus())
                .isEqualTo(UserPromotionStatus.COMPLETED);
        assertThatThrownBy(() -> voucherService.apply(apply(promotion, userId, orderId))).isInstanceOf(AppException.class);

        voucherService.release(new VoucherReleaseRequest(orderId, PromotionUsageStatus.REVOKED));
        assertThat(usageRepository.findByOrderId(orderId).orElseThrow().getStatus()).isEqualTo(PromotionUsageStatus.REVOKED);
        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isZero();
        assertThat(userPromotionRepository.findByUserIdAndPromotionId(userId, promotion.getId()).orElseThrow().getStatus())
                .isEqualTo(UserPromotionStatus.RESERVED);

        UUID expiringOrder = UUID.randomUUID();
        voucherService.apply(apply(promotion, userId, expiringOrder));
        voucherService.release(new VoucherReleaseRequest(expiringOrder, PromotionUsageStatus.EXPIRED));
        assertThat(usageRepository.findByOrderId(expiringOrder).orElseThrow().getStatus()).isEqualTo(PromotionUsageStatus.EXPIRED);
    }

    @Test
    void enforcesPerUserLimitAndKeepsVoucherWalletHistory() {
        Promotion promotion = savePromotion(DiscountType.FIXED_AMOUNT, new BigDecimal("10"), null, null, 10, null, null);
        promotion.setMaxUsagePerUser(1);
        promotionRepository.save(promotion);
        UUID userId = UUID.randomUUID();

        voucherService.apply(apply(promotion, userId, UUID.randomUUID()));
        assertThatThrownBy(() -> voucherService.apply(apply(promotion, userId, UUID.randomUUID())))
                .isInstanceOf(AppException.class);
        assertThat(voucherService.wallet(userId)).isEmpty();
    }

    @Test
    void keepsTotalQuotaAtomicUnderConcurrentApplyRequests() throws Exception {
        Promotion promotion = savePromotion(DiscountType.FIXED_AMOUNT, new BigDecimal("10"), null, null, 3, null, null);
        var executor = Executors.newFixedThreadPool(8);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            UUID userId = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            tasks.add(() -> {
                try {
                    voucherService.apply(apply(promotion, userId, orderId));
                    return true;
                } catch (AppException exception) {
                    return false;
                }
            });
        }
        long applied = executor.invokeAll(tasks).stream().filter(result -> {
            try { return result.get(); } catch (Exception e) { return false; }
        }).count();
        executor.shutdown();

        assertThat(applied).isEqualTo(3);
        assertThat(usageRepository.count()).isEqualTo(3);
        assertThat(promotionRepository.findById(promotion.getId()).orElseThrow().getUsedCount()).isEqualTo(3);
    }

    private Promotion savePromotion(DiscountType type, BigDecimal value, BigDecimal minAmount, BigDecimal maxDiscount,
                                    Integer totalLimit, LocalDateTime validFrom, LocalDateTime validTo) {
        return promotionRepository.save(Promotion.builder().code("V" + UUID.randomUUID().toString().substring(0, 8))
                .discountType(type).discountValue(value).minOrderAmount(minAmount).maxDiscountAmount(maxDiscount)
                .totalUsageLimit(totalLimit).maxUsagePerUser(10).usedCount(0).isActive(true).isCompensation(false)
                .validFrom(validFrom).validTo(validTo).build());
    }

    private VoucherValidationRequest validation(Promotion promotion, BigDecimal amount, UUID eventId) {
        return new VoucherValidationRequest(UUID.randomUUID(), eventId, promotion.getCode(), amount);
    }

    private VoucherApplyRequest apply(Promotion promotion, UUID userId, UUID orderId) {
        return new VoucherApplyRequest(orderId, userId, promotion.getEventId(), promotion.getCode(), new BigDecimal("100.00"));
    }
}
