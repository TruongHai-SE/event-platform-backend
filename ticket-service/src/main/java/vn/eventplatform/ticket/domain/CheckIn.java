package vn.eventplatform.ticket.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "check_ins",
    indexes = {
        @Index(name = "uq_check_ins_ticket_id", columnList = "ticket_id", unique = true),
        @Index(name = "idx_check_ins_staff_id", columnList = "staff_id"),
        @Index(name = "idx_check_ins_checked_in_at", columnList = "checked_in_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private Ticket ticket;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CheckInMethod method = CheckInMethod.QR_SCAN;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.checkedInAt == null) {
            this.checkedInAt = now;
        }
        if (this.createdAt == null) {
            this.createdAt = now;
        }
    }
}
