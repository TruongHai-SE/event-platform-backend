package vn.eventplatform.promotion.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;
import org.hibernate.annotations.Check;
import vn.eventplatform.promotion.domain.base.BaseEntity;
import vn.eventplatform.promotion.enums.PromotionUsageStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "promotion_usages", uniqueConstraints = {@UniqueConstraint(name = "uq_promotion_usage_order", columnNames = {"order_id"})}, indexes = {@Index(name = "idx_promotion_usages_user_id", columnList = "user_id"), @Index(name = "idx_promotion_usages_promotion_id", columnList = "promotion_id")})
@Check(constraints = "discount_amount >= 0")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionUsage extends BaseEntity {



    @Column(name = "promotion_id", nullable = false)
    private UUID promotionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @DecimalMin(value = "0.00")
    @Column(name = "discount_amount", precision = 15, scale = 2, nullable = false)
    private BigDecimal discountAmount;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PromotionUsageStatus status = PromotionUsageStatus.REVOKED;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (status == null) {
            status = PromotionUsageStatus.REVOKED;
        }

    }
}
