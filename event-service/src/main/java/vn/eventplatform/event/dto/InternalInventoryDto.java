package vn.eventplatform.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.eventplatform.event.domain.enums.EventStatus;
import vn.eventplatform.event.domain.enums.RefundPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InternalInventoryDto {

    private InternalInventoryDto() {
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketItemReservationRequest {
        @NotNull(message = "ticketTypeId không được để trống")
        private UUID ticketTypeId;

        @NotNull(message = "quantity không được để trống")
        @Min(value = 1, message = "Số lượng phải >= 1")
        private Integer quantity;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckAndReserveInventoryRequest {
        @NotNull(message = "eventId không được để trống")
        private UUID eventId;

        @NotNull(message = "showTimeId không được để trống")
        private UUID showTimeId;

        @NotEmpty(message = "Danh sách loại vé đặt giữ chỗ không được để trống")
        @Valid
        private List<TicketItemReservationRequest> items;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReleaseInventoryRequest {
        @NotNull(message = "showTimeId không được để trống")
        private UUID showTimeId;

        @NotEmpty(message = "Danh sách loại vé giải phóng không được để trống")
        @Valid
        private List<TicketItemReservationRequest> items;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmInventoryRequest {
        @NotNull(message = "showTimeId không được để trống")
        private UUID showTimeId;

        @NotEmpty(message = "Danh sách loại vé xác nhận bán không được để trống")
        @Valid
        private List<TicketItemReservationRequest> items;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketTypeInventoryDto {
        private UUID ticketTypeId;
        private String ticketTypeName;
        private BigDecimal price;
        private Integer maxPerOrder;
        private Integer availableQuantity;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckAndReserveInventoryResponse {
        private boolean success;
        private List<TicketTypeInventoryDto> reservedItems;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InternalEventSnapshotResponse {
        private UUID id;
        private UUID organizerId;
        private String title;
        private EventStatus status;
        private RefundPolicy refundPolicy;
        private Integer refundDeadlineHours;
        private BigDecimal refundFeePercent;
        private Instant nextShowTime;
    }
}
