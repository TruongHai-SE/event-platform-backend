package vn.eventplatform.event.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.eventplatform.event.domain.CategoryRequest;
import vn.eventplatform.event.domain.enums.ReviewStatus;

import java.util.List;
import java.util.UUID;

public interface CategoryRequestRepository extends JpaRepository<CategoryRequest, UUID> {

    boolean existsByNormalizedProposedNameAndStatus(String normalizedProposedName, ReviewStatus status);

    List<CategoryRequest> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);

    Page<CategoryRequest> findByRequesterId(UUID requesterId, Pageable pageable);

    Page<CategoryRequest> findByStatusOrderByCreatedAtAsc(ReviewStatus status, Pageable pageable);
}
