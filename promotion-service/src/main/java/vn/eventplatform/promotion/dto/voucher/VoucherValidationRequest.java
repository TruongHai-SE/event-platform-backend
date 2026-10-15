package vn.eventplatform.promotion.dto.voucher;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record VoucherValidationRequest(
        @NotNull UUID userId,
        UUID eventId,
        @NotBlank String code,
        @NotNull @DecimalMin("0.00") BigDecimal orderAmount
) {}
