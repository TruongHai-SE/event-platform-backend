package vn.eventplatform.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.eventplatform.event.domain.SeatSection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatSectionRepository extends JpaRepository<SeatSection, UUID> {

    List<SeatSection> findByEventIdOrderBySortOrderAscNameAsc(UUID eventId);

    Optional<SeatSection> findByIdAndEventId(UUID id, UUID eventId);

    boolean existsByEventIdAndCodeIgnoreCase(UUID eventId, String code);

    boolean existsByEventIdAndCodeIgnoreCaseAndIdNot(UUID eventId, String code, UUID id);
}
