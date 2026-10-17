package vn.eventplatform.order.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.order.client.dto.VoucherApplyRequest;
import vn.eventplatform.order.client.dto.VoucherCalculationResponse;
import vn.eventplatform.order.client.dto.VoucherReleaseRequest;
import vn.eventplatform.order.client.dto.VoucherValidationRequest;

@Slf4j
@Component
public class PromotionServiceFallback implements PromotionServiceClient {

    @Override
    public ApiResponse<VoucherCalculationResponse> validateVoucher(String secret, VoucherValidationRequest request) {
        log.warn("[PromotionServiceFallback] Không thể kết nối tới promotion-service để kiểm tra voucher: code={}",
                request != null ? request.getCode() : "null");
        throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Dịch vụ mã giảm giá hiện không khả dụng, vui lòng thử lại sau");
    }

    @Override
    public ApiResponse<VoucherCalculationResponse> applyVoucher(String secret, VoucherApplyRequest request) {
        log.warn("[PromotionServiceFallback] Không thể kết nối tới promotion-service để áp dụng voucher: orderId={}, code={}",
                request != null ? request.getOrderId() : "null", request != null ? request.getCode() : "null");
        throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Dịch vụ mã giảm giá hiện không khả dụng, vui lòng thử lại sau");
    }

    @Override
    public ApiResponse<Void> releaseVoucher(String secret, VoucherReleaseRequest request) {
        log.warn("[PromotionServiceFallback] Không thể kết nối tới promotion-service để giải phóng voucher: orderId={}, reason={}",
                request != null ? request.getOrderId() : "null", request != null ? request.getReason() : "null");
        return ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), "Dịch vụ khuyến mãi không khả dụng khi giải phóng voucher");
    }
}
