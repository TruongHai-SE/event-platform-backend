package vn.eventplatform.ticket.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.ticket.client.dto.InternalOrderResponse;

import java.util.UUID;

@Slf4j
@Component
public class OrderServiceFallback implements OrderServiceClient {

    @Override
    public ApiResponse<InternalOrderResponse> getOrderById(String secret, UUID id) {
        log.warn("Fallback kích hoạt khi gọi order-service getOrderById: orderId={}", id);
        return null;
    }
}
