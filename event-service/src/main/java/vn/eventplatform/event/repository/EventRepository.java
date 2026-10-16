package vn.eventplatform.event.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.eventplatform.event.domain.Event;
import vn.eventplatform.event.domain.enums.EventStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Event> findAll(Specification<Event> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Event> findWithCategoryById(UUID id);

    @EntityGraph(attributePaths = "category")
    Optional<Event> findWithCategoryBySlug(String slug);

    @EntityGraph(attributePaths = "category")
    Page<Event> findByOrganizerIdOrderByCreatedAtDesc(UUID organizerId, Pageable pageable);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    boolean existsByCategoryId(UUID categoryId);

    @Query("""
        SELECT e FROM Event e
        WHERE e.status = :status
          AND EXISTS (SELECT st FROM ShowTime st WHERE st.event = e AND st.status <> 'CANCELLED')
          AND NOT EXISTS (
              SELECT st FROM ShowTime st
              WHERE st.event = e AND st.status <> 'CANCELLED' AND st.endTime > :now
          )
    """)
    List<Event> findPublishedEventsReadyToComplete(@Param("status") EventStatus status, @Param("now") Instant now);
}
