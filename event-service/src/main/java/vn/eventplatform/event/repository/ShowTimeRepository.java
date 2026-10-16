package vn.eventplatform.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.eventplatform.event.domain.ShowTime;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowTimeRepository extends JpaRepository<ShowTime, UUID> {

    List<ShowTime> findByEventIdOrderByStartTimeAsc(UUID eventId);

    Optional<ShowTime> findByIdAndEventId(UUID id, UUID eventId);

    @Query("""
        SELECT CASE WHEN COUNT(st) > 0 THEN true ELSE false END
        FROM ShowTime st
        WHERE st.event.id = :eventId
          AND st.status <> vn.eventplatform.event.domain.enums.ShowTimeStatus.CANCELLED
          AND (:excludeId IS NULL OR st.id <> :excludeId)
          AND st.startTime < :endTime
          AND st.endTime > :startTime
    """)
    boolean existsOverlappingShowTime(
            @Param("eventId") UUID eventId,
            @Param("excludeId") UUID excludeId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );
}
