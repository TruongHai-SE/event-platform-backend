package vn.eventplatform.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRefundResponse {
    private int totalRequested;
    private int totalSuccess;
    private int totalFailed;
    private BigDecimal totalRefundedAmount;
    private int chunkSize;
    private double requestsPerSecond;
    private int maxRetries;
    private long itemTimeoutMs;
    @Builder.Default
    private List<BatchRefundItemResult> results = new ArrayList<>();
}
