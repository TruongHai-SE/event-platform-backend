package vn.eventplatform.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.eventplatform.event.domain.Seat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    @Query("""
        SELECT s FROM Seat s
        LEFT JOIN FETCH s.section
        LEFT JOIN FETCH s.ticketType
        WHERE s.event.id = :eventId
        ORDER BY s.rowLabel ASC, s.seatNumber ASC
    """)
    List<Seat> findByEventIdOrderByRowLabelAscSeatNumberAsc(@Param("eventId") UUID eventId);

    List<Seat> findBySectionIdOrderByRowLabelAscSeatNumberAsc(UUID sectionId);

    Optional<Seat> findByIdAndEventId(UUID id, UUID eventId);

    List<Seat> findByEventIdAndIdIn(UUID eventId, Collection<UUID> ids);

    boolean existsBySectionId(UUID sectionId);

    long countBySectionId(UUID sectionId);

    long countByEventIdAndTicketTypeId(UUID eventId, UUID ticketTypeId);
}
