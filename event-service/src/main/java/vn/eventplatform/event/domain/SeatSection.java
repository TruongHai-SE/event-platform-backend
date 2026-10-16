package vn.eventplatform.event.domain;

import jakarta.persistence.*;
import lombok.*;
import vn.eventplatform.common.entity.BaseAuditableEntity;

import java.util.UUID;

@Entity
@Table(name = "seat_sections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatSection extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Builder.Default
    @Column(name = "capacity", nullable = false)
    private Integer capacity = 0;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;
}
