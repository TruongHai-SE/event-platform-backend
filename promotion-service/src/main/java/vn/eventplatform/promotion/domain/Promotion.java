package vn.eventplatform.promotion.domain;

import jakarta.persistence.*;
import lombok.*;
import vn.eventplatform.promotion.domain.base.BaseEntity;
import vn.eventplatform.promotion.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "promotions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Promotion extends BaseEntity {

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "organizer_id")
    private UUID organizerId;

    @Column(name = "code", length = 50, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", precision = 15, scale = 2, nullable = false)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", precision = 15, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "max_discount_amount", precision = 15, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "total_usage_limit")
    private Integer totalUsageLimit;

    @Column(name = "used_count", nullable = false)
    private Integer usedCount;

    @Column(name = "max_usage_per_user")
    private Integer maxUsagePerUser;

    @Column(name = "is_compensation", nullable = false)
    private Boolean isCompensation;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_to")
    private LocalDateTime validTo;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
