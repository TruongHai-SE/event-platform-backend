package vn.eventplatform.ad.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.eventplatform.ad.enums.AdBillStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ad_bills", uniqueConstraints = @UniqueConstraint(name = "uq_ad_bills_campaign_id", columnNames = "campaign_id"))
@Getter
@Setter
@NoArgsConstructor
public class AdBill extends BaseEntity {
    @Column(name = "bill_code", nullable = false, unique = true, length = 40)
    private String billCode;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private AdCampaign campaign;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdBillStatus status;

    @Column(name = "paid_at")
    private Instant paidAt;
}
