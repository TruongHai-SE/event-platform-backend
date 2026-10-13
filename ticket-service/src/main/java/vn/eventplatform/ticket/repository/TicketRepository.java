package vn.eventplatform.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.domain.TicketStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    Optional<Ticket> findByQrPayload(String qrPayload);

    boolean existsByQrPayload(String qrPayload);

    List<Ticket> findByOrderId(UUID orderId);

    List<Ticket> findByShowTimeId(UUID showTimeId);

    long countByShowTimeIdAndStatus(UUID showTimeId, TicketStatus status);

    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.checkIn WHERE t.id = :id")
    Optional<Ticket> findByIdWithCheckIn(@Param("id") UUID id);

    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.checkIn WHERE t.qrPayload = :qrPayload")
    Optional<Ticket> findByQrPayloadWithCheckIn(@Param("qrPayload") String qrPayload);

    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.checkIn WHERE t.orderId = :orderId")
    List<Ticket> findByOrderIdWithCheckIn(@Param("orderId") UUID orderId);
}
