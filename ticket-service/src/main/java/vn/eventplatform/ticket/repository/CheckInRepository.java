package vn.eventplatform.ticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.eventplatform.ticket.domain.CheckIn;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckInRepository extends JpaRepository<CheckIn, UUID> {

    Optional<CheckIn> findByTicketId(UUID ticketId);

    boolean existsByTicketId(UUID ticketId);

    List<CheckIn> findByStaffId(UUID staffId);

    @Query("SELECT c FROM CheckIn c JOIN FETCH c.ticket WHERE c.ticket.id = :ticketId")
    Optional<CheckIn> findByTicketIdWithTicket(@Param("ticketId") UUID ticketId);

    @Query("SELECT c FROM CheckIn c JOIN FETCH c.ticket WHERE c.id = :id")
    Optional<CheckIn> findByIdWithTicket(@Param("id") UUID id);
}
