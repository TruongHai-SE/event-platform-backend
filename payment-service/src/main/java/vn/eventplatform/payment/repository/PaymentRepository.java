package vn.eventplatform.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.eventplatform.payment.domain.Payment;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.domain.PaymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrderId(UUID orderId);

    List<Payment> findByOrderIdAndStatus(UUID orderId, PaymentStatus status);

    Optional<Payment> findByOrderCode(String orderCode);

    List<Payment> findByOrderCodeOrderByCreatedAtDesc(String orderCode);

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByMethodAndTransactionId(PaymentGateway method, String transactionId);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(UUID orderId, PaymentStatus status);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant threshold);

    @Query("SELECT p FROM Payment p WHERE p.orderCode = :orderCode AND p.status = :status ORDER BY p.createdAt DESC")
    List<Payment> findByOrderCodeAndStatus(@Param("orderCode") String orderCode, @Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE REPLACE(p.orderCode, '-', '') = REPLACE(:orderCode, '-', '') AND p.status = :status ORDER BY p.createdAt DESC")
    List<Payment> findByNormalizedOrderCodeAndStatus(@Param("orderCode") String orderCode, @Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE REPLACE(p.orderCode, '-', '') = REPLACE(:orderCode, '-', '') ORDER BY p.createdAt DESC")
    List<Payment> findByNormalizedOrderCode(@Param("orderCode") String orderCode);

    boolean existsByOrderIdAndStatusIn(UUID orderId, List<PaymentStatus> statuses);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :newStatus, p.transactionId = :transactionId, p.paidAt = :paidAt, p.updatedAt = :now WHERE p.id = :id AND p.status = :expectedStatus")
    int updatePaymentStatusIfMatch(
            @Param("id") UUID id,
            @Param("newStatus") PaymentStatus newStatus,
            @Param("expectedStatus") PaymentStatus expectedStatus,
            @Param("transactionId") String transactionId,
            @Param("paidAt") Instant paidAt,
            @Param("now") Instant now
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :newStatus, p.updatedAt = :now WHERE p.orderId = :orderId AND p.id <> :excludeId AND p.status = :expectedStatus")
    int cancelOtherPendingPayments(
            @Param("orderId") UUID orderId,
            @Param("excludeId") UUID excludeId,
            @Param("newStatus") PaymentStatus newStatus,
            @Param("expectedStatus") PaymentStatus expectedStatus,
            @Param("now") Instant now
    );
}

