package vn.eventplatform.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.eventplatform.event.domain.TicketType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByEventIdOrderByPriceAsc(UUID eventId);

    Optional<TicketType> findByIdAndEventId(UUID id, UUID eventId);
}
