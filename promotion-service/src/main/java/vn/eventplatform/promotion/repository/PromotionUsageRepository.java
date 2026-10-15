package vn.eventplatform.promotion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.eventplatform.promotion.domain.PromotionUsage;
import vn.eventplatform.promotion.enums.PromotionUsageStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PromotionUsageRepository
        extends JpaRepository<PromotionUsage, UUID> {

    Optional<PromotionUsage> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    List<PromotionUsage> findByUserId(UUID userId);

    List<PromotionUsage> findByPromotionId(UUID promotionId);

    List<PromotionUsage> findByUserIdAndPromotionId(
            UUID userId,
            UUID promotionId
    );

    List<PromotionUsage> findByUserIdAndStatus(
            UUID userId,
            PromotionUsageStatus status
    );

    List<PromotionUsage> findByPromotionIdAndStatus(
            UUID promotionId,
            PromotionUsageStatus status
    );

    long countByUserIdAndPromotionIdAndStatus(UUID userId, UUID promotionId, PromotionUsageStatus status);
}
