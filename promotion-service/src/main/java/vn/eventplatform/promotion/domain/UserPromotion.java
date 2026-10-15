package vn.eventplatform.promotion.domain;

import jakarta.persistence.*;
import lombok.*;
import vn.eventplatform.promotion.domain.base.BaseEntity;
import vn.eventplatform.promotion.enums.UserPromotionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_promotions", uniqueConstraints = {@UniqueConstraint(name = "uq_user_promotion", columnNames = {"user_id", "promotion_id"})}, indexes = {@Index(name = "idx_user_promotions_user_id", columnList = "user_id"), @Index(name = "idx_user_promotions_promotion_id", columnList = "promotion_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPromotion extends BaseEntity {


    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "promotion_id", nullable = false)
    private UUID promotionId;

    @Column(name = "source", length = 30, nullable = false)
    private String source;

    @Column(name = "source_broadcast_id")
    private UUID sourceBroadcastId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserPromotionStatus status = UserPromotionStatus.RESERVED;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        LocalDateTime now = LocalDateTime.now();

        if (status == null) {
            status = UserPromotionStatus.RESERVED;
        }

        if (issuedAt == null) {
            issuedAt = now;
        }
    }
}
