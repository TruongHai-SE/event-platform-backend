package vn.eventplatform.promotion.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.eventplatform.promotion.domain.UserPromotion;
import vn.eventplatform.promotion.enums.UserPromotionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserPromotionRepository
        extends JpaRepository<UserPromotion, UUID> {

    List<UserPromotion> findByUserId(UUID userId);

    List<UserPromotion> findByPromotionId(UUID promotionId);

    Optional<UserPromotion> findByUserIdAndPromotionId(
            UUID userId,
            UUID promotionId
    );

    List<UserPromotion> findByUserIdAndStatus(
            UUID userId,
            UserPromotionStatus status
    );

    List<UserPromotion> findBySource(String source);

    List<UserPromotion> findBySourceBroadcastId(
            UUID sourceBroadcastId
    );

    boolean existsByUserIdAndPromotionId(
            UUID userId,
            UUID promotionId
    );

    List<UserPromotion> findByUserIdOrderByIssuedAtDesc(UUID userId);
}
