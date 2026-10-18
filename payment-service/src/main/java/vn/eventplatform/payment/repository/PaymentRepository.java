package vn.eventplatform.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.eventplatform.payment.domain.Payment;
import vn.eventplatform.payment.domain.PaymentGateway;
import vn.eventplatform.payment.domain.PaymentStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByOrderCode(String orderCode);

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByMethodAndTransactionId(PaymentGateway method, String transactionId);

    Optional<Payment> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(UUID orderId, PaymentStatus status);

    @Query("SELECT p FROM Payment p WHERE p.orderCode = :orderCode AND p.status = :status ORDER BY p.createdAt DESC")
    List<Payment> findByOrderCodeAndStatus(@Param("orderCode") String orderCode, @Param("status") PaymentStatus status);

    boolean existsByOrderIdAndStatusIn(UUID orderId, List<PaymentStatus> statuses);
}
