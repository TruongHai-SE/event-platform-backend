package vn.eventplatform.event.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import vn.eventplatform.common.entity.BaseAuditableEntity;
import vn.eventplatform.event.domain.enums.EventStatus;
import vn.eventplatform.event.domain.enums.RefundPolicy;
import vn.eventplatform.event.domain.enums.SeatingMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organizer_id", nullable = false)
    private UUID organizerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "slug", nullable = false, unique = true, length = 550)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "venue_name", length = 255)
    private String venueName;

    @Column(name = "venue_address", length = 500)
    private String venueAddress;

    @Column(name = "banner_url", length = 500)
    private String bannerUrl;

    @Column(name = "banner_public_id", length = 255)
    private String bannerPublicId;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "thumbnail_public_id", length = 255)
    private String thumbnailPublicId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private EventStatus status = EventStatus.DRAFT;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "seating_mode", nullable = false, length = 30)
    private SeatingMode seatingMode = SeatingMode.GENERAL_ADMISSION;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "refund_policy", nullable = false, length = 30)
    private RefundPolicy refundPolicy = RefundPolicy.NO_REFUND;

    @Column(name = "refund_deadline_days")
    private Integer refundDeadlineDays;

    @Column(name = "refund_fee_percentage", precision = 5, scale = 2)
    private BigDecimal refundFeePercentage;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Version
    @Builder.Default
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShowTime> showTimes = new ArrayList<>();

    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketType> ticketTypes = new ArrayList<>();

    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SeatSection> seatSections = new ArrayList<>();

    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Seat> seats = new ArrayList<>();
}
