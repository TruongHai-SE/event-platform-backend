package vn.eventplatform.event.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.event.dto.InternalInventoryDto;
import vn.eventplatform.event.service.InventoryInternalService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/events")
@RequiredArgsConstructor
@Tag(name = "Internal Event & Inventory API", description = "Các API nội bộ phục vụ order-service và ticket-service")
public class InternalEventController {

    private final InventoryInternalService inventoryInternalService;

    @Value("${security.internal.secret:internal-secret-key}")
    private String internalSecret;

    @PostMapping("/inventory/check-and-reserve")
    @Operation(summary = "[Internal] Kiểm tra tồn kho và giữ chỗ vé nguyên tử (PESSIMISTIC_WRITE)")
    public ResponseEntity<ApiResponse<InternalInventoryDto.CheckAndReserveInventoryResponse>> checkAndReserve(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody InternalInventoryDto.CheckAndReserveInventoryRequest request
    ) {
        validateInternalSecretConstantTime(secret);
        InternalInventoryDto.CheckAndReserveInventoryResponse response =
                inventoryInternalService.checkAndReserve(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/inventory/release")
    @Operation(summary = "[Internal] Giải phóng tồn kho vé giữ chỗ khi đơn hàng hết hạn hoặc hủy")
    public ResponseEntity<ApiResponse<Void>> releaseInventory(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody InternalInventoryDto.ReleaseInventoryRequest request
    ) {
        validateInternalSecretConstantTime(secret);
        inventoryInternalService.releaseInventory(request);
        return ResponseEntity.ok(ApiResponse.ok("Đã giải phóng tồn kho vé giữ chỗ", null));
    }

    @PostMapping("/inventory/confirm")
    @Operation(summary = "[Internal] Xác nhận chuyển tồn kho từ giữ chỗ (reserved) sang đã bán (sold)")
    public ResponseEntity<ApiResponse<Void>> confirmInventory(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody InternalInventoryDto.ConfirmInventoryRequest request
    ) {
        validateInternalSecretConstantTime(secret);
        inventoryInternalService.confirmInventory(request);
        return ResponseEntity.ok(ApiResponse.ok("Đã xác nhận bán vé thành công", null));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "[Internal] Lấy thông tin snapshot sự kiện và chính sách hoàn vé")
    public ResponseEntity<ApiResponse<InternalInventoryDto.InternalEventSnapshotResponse>> getEventSnapshot(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable("eventId") UUID eventId
    ) {
        validateInternalSecretConstantTime(secret);
        return ResponseEntity.ok(ApiResponse.ok(inventoryInternalService.getEventSnapshot(eventId)));
    }

    private void validateInternalSecretConstantTime(String providedSecret) {
        if (providedSecret == null || internalSecret == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Khóa xác thực nội bộ (X-Internal-Secret) không hợp lệ");
        }
        boolean matched = MessageDigest.isEqual(
                providedSecret.getBytes(StandardCharsets.UTF_8),
                internalSecret.getBytes(StandardCharsets.UTF_8)
        );
        if (!matched) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Khóa xác thực nội bộ (X-Internal-Secret) không hợp lệ");
        }
    }
}
