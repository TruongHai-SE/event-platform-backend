package vn.eventplatform.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.eventplatform.order.domain.Order;
import vn.eventplatform.order.domain.OrderStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    List<Order> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    List<Order> findByEventId(UUID eventId);

    List<Order> findByShowTimeId(UUID showTimeId);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.orderCode = :orderCode")
    Optional<Order> findByOrderCodeWithItems(@Param("orderCode") String orderCode);

    List<Order> findByStatusAndExpiresAtBefore(OrderStatus status, Instant now);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.status = :status AND o.expiresAt < :now")
    List<Order> findByStatusAndExpiresAtBeforeWithItems(@Param("status") OrderStatus status, @Param("now") Instant now);
}
