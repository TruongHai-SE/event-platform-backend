package vn.eventplatform.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InternalEventSnapshotResponse {
    private UUID id;
    private UUID organizerId;
    private String title;
    private String status;
    private String refundPolicy; // "NO_REFUND", "ALLOW_REFUND"
    private Integer refundDeadlineDays;
    private Integer refundDeadlineHours;
    private BigDecimal refundFeePercent;
    private Instant nextShowTime;
}
