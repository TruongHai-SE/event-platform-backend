package vn.eventplatform.ad.domain;
import jakarta.persistence.*; import lombok.*; import java.math.BigDecimal;
@Entity @Table(name="ad_packages") @Getter @Setter @NoArgsConstructor public class AdPackage extends BaseEntity { @Column(nullable=false,unique=true) private String name; private String description; @Column(nullable=false) private BigDecimal price; @Column(name="duration_days",nullable=false) private Integer durationDays; @Column(nullable=false) private Boolean active=true; }
