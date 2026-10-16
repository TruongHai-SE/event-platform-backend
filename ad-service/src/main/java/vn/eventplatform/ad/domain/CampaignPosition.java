package vn.eventplatform.ad.domain;

import jakarta.persistence.*;
import lombok.*;
import vn.eventplatform.ad.enums.BannerPosition;
import java.math.BigDecimal;

@Entity
@Table(name = "campaign_positions", uniqueConstraints = @UniqueConstraint(name = "uk_campaign_position", columnNames = {"campaign_id", "position"}))
@Getter @Setter @NoArgsConstructor
public class CampaignPosition extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private AdCampaign campaign;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BannerPosition position;
    @Column(name = "bonus_price", nullable = false)
    private BigDecimal bonusPrice;
}
