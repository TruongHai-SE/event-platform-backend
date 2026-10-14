package vn.eventplatform.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.order.client.dto.CheckAndReserveInventoryRequest;
import vn.eventplatform.order.client.dto.CheckAndReserveInventoryResponse;
import vn.eventplatform.order.client.dto.ReleaseInventoryRequest;

@FeignClient(name = "event-service", fallback = EventServiceFallback.class)
public interface EventServiceClient {

    @PostMapping("/internal/v1/events/inventory/check-and-reserve")
    ApiResponse<CheckAndReserveInventoryResponse> checkAndReserveInventory(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody CheckAndReserveInventoryRequest request
    );

    @PostMapping("/internal/v1/events/inventory/release")
    ApiResponse<Void> releaseInventory(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody ReleaseInventoryRequest request
    );
}
