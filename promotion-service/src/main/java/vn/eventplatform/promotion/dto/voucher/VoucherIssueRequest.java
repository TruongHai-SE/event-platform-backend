package vn.eventplatform.promotion.dto.voucher;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record VoucherIssueRequest(@NotNull UUID userId, @NotBlank String source, UUID sourceBroadcastId) {}
