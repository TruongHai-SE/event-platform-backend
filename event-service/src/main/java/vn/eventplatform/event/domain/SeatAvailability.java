package vn.eventplatform.event.domain;

import jakarta.persistence.*;
import lombok.*;
import vn.eventplatform.common.entity.BaseAuditableEntity;
import vn.eventplatform.event.domain.enums.SeatAvailabilityStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seat_availabilities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatAvailability extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_time_id", nullable = false)
    private ShowTime showTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SeatAvailabilityStatus status = SeatAvailabilityStatus.AVAILABLE;

    @Column(name = "held_until")
    private Instant heldUntil;

    @Column(name = "order_id")
    private UUID orderId;
}
