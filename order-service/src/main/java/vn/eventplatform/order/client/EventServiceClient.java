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

    @PostMapping("/internal/v1/events/inventory/release-sold")
    ApiResponse<Void> releaseSoldInventory(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody ReleaseInventoryRequest request
    );

    @org.springframework.web.bind.annotation.GetMapping("/internal/v1/events/inventory/showtimes/{showTimeId}")
    ApiResponse<java.util.List<vn.eventplatform.order.client.dto.ShowTimeInventoryResponse>> getShowTimeInventories(
            @RequestHeader("X-Internal-Secret") String secret,
            @org.springframework.web.bind.annotation.PathVariable("showTimeId") java.util.UUID showTimeId
    );

    @org.springframework.web.bind.annotation.GetMapping("/internal/v1/events/{eventId}")
    ApiResponse<vn.eventplatform.order.client.dto.InternalEventSnapshotResponse> getEventSnapshot(
            @RequestHeader("X-Internal-Secret") String secret,
            @org.springframework.web.bind.annotation.PathVariable("eventId") java.util.UUID eventId
    );

    @org.springframework.web.bind.annotation.GetMapping("/internal/v1/events/showtimes/{showTimeId}")
    ApiResponse<vn.eventplatform.order.client.dto.ShowTimeResponse> getShowTime(
            @RequestHeader("X-Internal-Secret") String secret,
            @org.springframework.web.bind.annotation.PathVariable("showTimeId") java.util.UUID showTimeId
    );
}
