package vn.eventplatform.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.eventplatform.payment.domain.RefundLog;
import vn.eventplatform.payment.domain.RefundStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundLogRepository extends JpaRepository<RefundLog, UUID> {

    List<RefundLog> findByOrderId(UUID orderId);

    List<RefundLog> findByPaymentId(UUID paymentId);

    Optional<RefundLog> findByIdempotencyKey(String idempotencyKey);

    Optional<RefundLog> findByGatewayRefundId(String gatewayRefundId);

    List<RefundLog> findByStatusOrderByCreatedAtDesc(RefundStatus status);
}
