package vn.eventplatform.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchRefundByOrdersRequest {
    private List<InternalRefundOrderRequest> requests;
    private Integer chunkSize;
    private Double requestsPerSecond;
}
