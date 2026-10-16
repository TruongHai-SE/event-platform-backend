package vn.eventplatform.event.domain;

import jakarta.persistence.*;
import lombok.*;
import vn.eventplatform.common.entity.BaseAuditableEntity;
import vn.eventplatform.event.domain.enums.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "proposed_name", nullable = false, length = 100)
    private String proposedName;

    @Column(name = "normalized_proposed_name", nullable = false, length = 100)
    private String normalizedProposedName;

    @Column(name = "description", length = 1000)
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReviewStatus status = ReviewStatus.PENDING;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "category_id")
    private UUID categoryId;
}
