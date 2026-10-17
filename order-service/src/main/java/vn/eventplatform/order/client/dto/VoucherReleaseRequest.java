package vn.eventplatform.order.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherReleaseRequest {

    @NotNull(message = "Order ID không được để trống")
    private UUID orderId;

    @NotBlank(message = "Lý do giải phóng không được để trống")
    private String reason; // EXPIRED, REVOKED
}
