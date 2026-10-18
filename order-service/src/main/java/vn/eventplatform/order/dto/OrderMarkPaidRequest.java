package vn.eventplatform.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderMarkPaidRequest {
    private UUID paymentId;
    private String transactionId;
    private String paymentMethod;
    private Instant paidAt;
}
