package vn.eventplatform.promotion.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.promotion.dto.voucher.*;
import vn.eventplatform.promotion.service.InternalRequestGuard;
import vn.eventplatform.promotion.service.VoucherApplicationService;

@RestController
@RequestMapping("/internal/v1/vouchers")
@RequiredArgsConstructor
public class InternalVoucherController {
    private final VoucherApplicationService voucherApplicationService;
    private final InternalRequestGuard internalRequestGuard;

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<VoucherCalculationResponse>> validate(
            @RequestHeader("X-Internal-Secret") String secret, @Valid @RequestBody VoucherValidationRequest request) {
        internalRequestGuard.verify(secret);
        return ResponseEntity.ok(ApiResponse.ok(voucherApplicationService.validate(request)));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<VoucherCalculationResponse>> apply(
            @RequestHeader("X-Internal-Secret") String secret, @Valid @RequestBody VoucherApplyRequest request) {
        internalRequestGuard.verify(secret);
        return ResponseEntity.ok(ApiResponse.ok("Áp dụng voucher thành công", voucherApplicationService.apply(request)));
    }

    @PostMapping("/release")
    public ResponseEntity<ApiResponse<Void>> release(
            @RequestHeader("X-Internal-Secret") String secret, @Valid @RequestBody VoucherReleaseRequest request) {
        internalRequestGuard.verify(secret);
        voucherApplicationService.release(request);
        return ResponseEntity.ok(ApiResponse.ok("Đã giải phóng voucher", null));
    }
}
