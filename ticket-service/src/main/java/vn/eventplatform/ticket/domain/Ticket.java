package vn.eventplatform.ticket.domain;

import jakarta.persistence.*;
import lombok.*;
import vn.eventplatform.common.entity.BaseAuditableEntity;

import java.util.UUID;

@Entity
@Table(
    name = "tickets",
    indexes = {
        @Index(name = "idx_tickets_order_id", columnList = "order_id"),
        @Index(name = "idx_tickets_show_time_id", columnList = "show_time_id"),
        @Index(name = "uq_tickets_qr_payload", columnList = "qr_payload", unique = true)
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ticket extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "ticket_type_id", nullable = false)
    private UUID ticketTypeId;

    @Column(name = "show_time_id", nullable = false)
    private UUID showTimeId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "seat_id")
    private UUID seatId;

    @Column(name = "qr_payload", unique = true, columnDefinition = "TEXT")
    private String qrPayload;

    @Column(name = "seat_number", length = 20)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private TicketStatus status = TicketStatus.SOLD;

    @OneToOne(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private CheckIn checkIn;
}
