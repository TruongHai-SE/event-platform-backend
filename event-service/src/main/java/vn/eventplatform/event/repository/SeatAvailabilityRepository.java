package vn.eventplatform.event.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.eventplatform.event.domain.SeatAvailability;
import vn.eventplatform.event.domain.enums.SeatAvailabilityStatus;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SeatAvailabilityRepository extends JpaRepository<SeatAvailability, UUID> {

    List<SeatAvailability> findByShowTimeId(UUID showTimeId);

    boolean existsByShowTimeIdAndSeatId(UUID showTimeId, UUID seatId);

    @Query("""
        SELECT CASE WHEN COUNT(sa) > 0 THEN true ELSE false END
        FROM SeatAvailability sa
        WHERE sa.seat.id IN :seatIds AND sa.status IN :activeStatuses
    """)
    boolean existsBySeatIdInAndStatusIn(
            @Param("seatIds") Collection<UUID> seatIds,
            @Param("activeStatuses") Collection<SeatAvailabilityStatus> activeStatuses
    );
}
