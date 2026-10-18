package vn.eventplatform.payment.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.payment.client.dto.InternalOrderResponse;
import vn.eventplatform.payment.client.dto.OrderMarkPaidRequest;

import java.util.UUID;

@Slf4j
@Component
public class OrderServiceFallback implements OrderServiceClient {

    @Override
    public ApiResponse<InternalOrderResponse> getOrderById(String secret, UUID id) {
        log.error("Fallback: Không thể kết nối tới order-service để lấy đơn hàng id={}", id);
        return ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), "Order service tạm thời không khả dụng");
    }

    @Override
    public ApiResponse<InternalOrderResponse> getOrderByCode(String secret, String orderCode) {
        log.error("Fallback: Không thể kết nối tới order-service để lấy đơn hàng orderCode={}", orderCode);
        return ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), "Order service tạm thời không khả dụng");
    }

    @Override
    public ApiResponse<InternalOrderResponse> markOrderPaid(String secret, UUID id, OrderMarkPaidRequest request) {
        log.error("Fallback: Không thể kết nối tới order-service để xác nhận thanh toán đơn hàng id={}", id);
        return ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), "Order service tạm thời không khả dụng");
    }
}
