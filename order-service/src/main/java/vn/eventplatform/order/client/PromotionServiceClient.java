package vn.eventplatform.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.order.client.dto.VoucherCalculationResponse;
import vn.eventplatform.order.client.dto.VoucherValidationRequest;

@FeignClient(name = "promotion-service", fallback = PromotionServiceFallback.class)
public interface PromotionServiceClient {

    @PostMapping("/internal/v1/vouchers/validate")
    ApiResponse<VoucherCalculationResponse> validateVoucher(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody VoucherValidationRequest request
    );
}
