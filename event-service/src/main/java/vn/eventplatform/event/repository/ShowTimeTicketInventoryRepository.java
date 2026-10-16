package vn.eventplatform.event.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.eventplatform.event.domain.ShowTimeTicketInventory;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowTimeTicketInventoryRepository extends JpaRepository<ShowTimeTicketInventory, UUID> {

    @Query("""
        SELECT inv FROM ShowTimeTicketInventory inv
        JOIN FETCH inv.ticketType
        WHERE inv.showTime.id = :showTimeId
    """)
    List<ShowTimeTicketInventory> findByShowTimeIdWithTicketType(@Param("showTimeId") UUID showTimeId);

    @Query("""
        SELECT inv FROM ShowTimeTicketInventory inv
        JOIN FETCH inv.ticketType tt
        JOIN FETCH inv.showTime st
        WHERE st.event.id = :eventId
    """)
    List<ShowTimeTicketInventory> findByEventId(@Param("eventId") UUID eventId);

    List<ShowTimeTicketInventory> findByTicketTypeId(UUID ticketTypeId);

    List<ShowTimeTicketInventory> findByShowTimeId(UUID showTimeId);

    boolean existsByShowTimeIdAndTicketTypeId(UUID showTimeId, UUID ticketTypeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT inv FROM ShowTimeTicketInventory inv
        JOIN FETCH inv.ticketType tt
        WHERE inv.showTime.id = :showTimeId AND tt.id = :ticketTypeId
    """)
    Optional<ShowTimeTicketInventory> findAndLockByShowTimeAndTicketType(
            @Param("showTimeId") UUID showTimeId,
            @Param("ticketTypeId") UUID ticketTypeId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT inv FROM ShowTimeTicketInventory inv
        JOIN FETCH inv.ticketType tt
        WHERE inv.showTime.id = :showTimeId AND tt.id IN :ticketTypeIds
        ORDER BY tt.id ASC
    """)
    List<ShowTimeTicketInventory> findByShowTimeIdAndTicketTypeIdInForUpdate(
            @Param("showTimeId") UUID showTimeId,
            @Param("ticketTypeIds") Collection<UUID> ticketTypeIds
    );

    @Query("""
        SELECT CASE WHEN COUNT(inv) > 0 THEN true ELSE false END
        FROM ShowTimeTicketInventory inv
        WHERE inv.showTime.id = :showTimeId AND (inv.reservedQuantity > 0 OR inv.soldQuantity > 0)
    """)
    boolean hasActiveReservationsOrSalesByShowTime(@Param("showTimeId") UUID showTimeId);

    @Query("""
        SELECT CASE WHEN COUNT(inv) > 0 THEN true ELSE false END
        FROM ShowTimeTicketInventory inv
        WHERE inv.ticketType.id = :ticketTypeId AND (inv.reservedQuantity > 0 OR inv.soldQuantity > 0)
    """)
    boolean hasActiveReservationsOrSalesByTicketType(@Param("ticketTypeId") UUID ticketTypeId);
}
