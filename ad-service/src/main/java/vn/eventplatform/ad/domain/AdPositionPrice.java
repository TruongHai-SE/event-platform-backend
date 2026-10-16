package vn.eventplatform.ad.domain;

import jakarta.persistence.*;
import lombok.*;
import vn.eventplatform.ad.enums.BannerPosition;
import java.math.BigDecimal;

@Entity
@Table(name = "ad_position_prices")
@Getter @Setter @NoArgsConstructor
public class AdPositionPrice extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private BannerPosition position;
    @Column(name = "bonus_price", nullable = false)
    private BigDecimal bonusPrice;
    @Column(nullable = false)
    private Boolean active = true;
}
