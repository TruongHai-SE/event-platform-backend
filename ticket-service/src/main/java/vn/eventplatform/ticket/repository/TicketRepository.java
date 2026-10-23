package vn.eventplatform.ticket.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.eventplatform.ticket.domain.Ticket;
import vn.eventplatform.ticket.domain.TicketStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByQrPayload(String qrPayload);

    boolean existsByQrPayload(String qrPayload);

    boolean existsByOrderId(UUID orderId);

    List<Ticket> findByOrderId(UUID orderId);

    List<Ticket> findByUserId(UUID userId);

    List<Ticket> findByShowTimeId(UUID showTimeId);

    long countByShowTimeIdAndStatus(UUID showTimeId, TicketStatus status);

    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.checkIn WHERE t.id = :id")
    Optional<Ticket> findByIdWithCheckIn(@Param("id") UUID id);

    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.checkIn WHERE t.qrPayload = :qrPayload")
    Optional<Ticket> findByQrPayloadWithCheckIn(@Param("qrPayload") String qrPayload);

    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.checkIn WHERE t.orderId = :orderId")
    List<Ticket> findByOrderIdWithCheckIn(@Param("orderId") UUID orderId);

    @Query("SELECT t FROM Ticket t LEFT JOIN FETCH t.checkIn WHERE t.userId = :userId ORDER BY t.createdAt DESC")
    List<Ticket> findByUserIdWithCheckIn(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"checkIn"})
    Page<Ticket> findAll(Specification<Ticket> spec, Pageable pageable);

    List<Ticket> findByShowTimeIdIn(List<UUID> showTimeIds);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Ticket t SET t.status = :newStatus WHERE t.showTimeId IN :showTimeIds AND t.status != :newStatus")
    int cancelTicketsByShowTimeIds(@Param("showTimeIds") List<UUID> showTimeIds, @Param("newStatus") TicketStatus newStatus);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Ticket t SET t.status = :newStatus WHERE t.showTimeId = :showTimeId AND t.status != :newStatus")
    int cancelTicketsByShowTimeId(@Param("showTimeId") UUID showTimeId, @Param("newStatus") TicketStatus newStatus);

    long countByOrderIdAndStatus(UUID orderId, TicketStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Ticket t SET t.status = :newStatus WHERE t.orderId = :orderId AND t.status != :newStatus")
    int cancelTicketsByOrderId(@Param("orderId") UUID orderId, @Param("newStatus") TicketStatus newStatus);

    @Query("SELECT t.status as status, COUNT(t) as count " +
           "FROM Ticket t " +
           "WHERE (:eventId IS NULL OR t.eventId = :eventId) " +
           "  AND (:showTimeId IS NULL OR t.showTimeId = :showTimeId) " +
           "GROUP BY t.status")
    List<vn.eventplatform.ticket.repository.projection.StatusCountProjection> countByStatus(
            @Param("eventId") UUID eventId,
            @Param("showTimeId") UUID showTimeId
    );

    @Query("SELECT t.ticketTypeId as ticketTypeId, t.ticketTypeName as ticketTypeName, t.status as status, COUNT(t) as count " +
           "FROM Ticket t " +
           "WHERE (:eventId IS NULL OR t.eventId = :eventId) " +
           "  AND (:showTimeId IS NULL OR t.showTimeId = :showTimeId) " +
           "GROUP BY t.ticketTypeId, t.ticketTypeName, t.status")
    List<vn.eventplatform.ticket.repository.projection.TicketTypeStatusCountProjection> countByTicketTypeAndStatus(
            @Param("eventId") UUID eventId,
            @Param("showTimeId") UUID showTimeId
    );
}
