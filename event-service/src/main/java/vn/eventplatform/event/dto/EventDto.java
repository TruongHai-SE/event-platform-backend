package vn.eventplatform.event.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.eventplatform.event.domain.enums.EventStatus;
import vn.eventplatform.event.domain.enums.RefundPolicy;
import vn.eventplatform.event.domain.enums.SeatAvailabilityStatus;
import vn.eventplatform.event.domain.enums.SeatingMode;
import vn.eventplatform.event.domain.enums.ShowTimeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EventDto {

    private EventDto() {
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateEventRequest {
        @NotBlank(message = "Tiêu đề sự kiện không được để trống")
        @Size(max = 250, message = "Tiêu đề sự kiện tối đa 250 ký tự")
        private String title;

        private String description;

        @NotNull(message = "Danh mục sự kiện không được để trống")
        private UUID categoryId;

        @Size(max = 255)
        private String venueName;

        @Size(max = 100)
        private String city;

        private String address;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private SeatingMode seatingMode;
        private RefundPolicy refundPolicy;

        @Min(value = 0, message = "Số giờ hoàn vé trước sự kiện phải >= 0")
        private Integer refundDeadlineHours;

        @DecimalMin(value = "0.00")
        @DecimalMax(value = "100.00")
        private BigDecimal refundFeePercent;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateEventRequest {
        @NotBlank(message = "Tiêu đề sự kiện không được để trống")
        @Size(max = 250, message = "Tiêu đề sự kiện tối đa 250 ký tự")
        private String title;

        private String description;

        @NotNull(message = "Danh mục sự kiện không được để trống")
        private UUID categoryId;

        @Size(max = 255)
        private String venueName;

        @Size(max = 100)
        private String city;

        private String address;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private SeatingMode seatingMode;
        private RefundPolicy refundPolicy;

        @Min(value = 0, message = "Số giờ hoàn vé trước sự kiện phải >= 0")
        private Integer refundDeadlineHours;

        @DecimalMin(value = "0.00")
        @DecimalMax(value = "100.00")
        private BigDecimal refundFeePercent;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRefundPolicyRequest {
        @NotNull(message = "Chính sách hoàn vé không được để trống")
        private RefundPolicy refundPolicy;

        @Min(value = 0, message = "Số giờ hoàn vé trước sự kiện phải >= 0")
        private Integer refundDeadlineHours;

        @DecimalMin(value = "0.00")
        @DecimalMax(value = "100.00")
        private BigDecimal refundFeePercent;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventMediaUrlRequest {
        private String bannerUrl;
        private String thumbnailUrl;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateShowTimeRequest {
        @NotNull(message = "Thời gian bắt đầu không được để trống")
        private Instant startTime;

        @NotNull(message = "Thời gian kết thúc không được để trống")
        private Instant endTime;

        private String venueAddress;

        @Min(value = 1, message = "Sức chứa suất diễn phải >= 1")
        private Integer capacity;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateShowTimeRequest {
        @NotNull(message = "Thời gian bắt đầu không được để trống")
        private Instant startTime;

        @NotNull(message = "Thời gian kết thúc không được để trống")
        private Instant endTime;

        private String venueAddress;

        @Min(value = 1, message = "Sức chứa suất diễn phải >= 1")
        private Integer capacity;

        private ShowTimeStatus status;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateTicketTypeRequest {
        @NotBlank(message = "Tên loại vé không được để trống")
        @Size(max = 150)
        private String name;

        private String description;

        @NotNull(message = "Giá vé không được để trống")
        @DecimalMin(value = "0.00", message = "Giá vé không được âm")
        private BigDecimal price;

        @NotNull(message = "Tổng số lượng vé không được để trống")
        @Min(value = 1, message = "Tổng số lượng vé phải >= 1")
        private Integer totalQuantity;

        @Min(value = 1, message = "Số lượng tối đa mỗi đơn phải >= 1")
        private Integer maxPerOrder;

        private Instant saleStartTime;
        private Instant saleEndTime;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateTicketTypeRequest {
        @NotBlank(message = "Tên loại vé không được để trống")
        @Size(max = 150)
        private String name;

        private String description;

        @NotNull(message = "Giá vé không được để trống")
        @DecimalMin(value = "0.00", message = "Giá vé không được âm")
        private BigDecimal price;

        @NotNull(message = "Tổng số lượng vé không được để trống")
        @Min(value = 1, message = "Tổng số lượng vé phải >= 1")
        private Integer totalQuantity;

        @Min(value = 1, message = "Số lượng tối đa mỗi đơn phải >= 1")
        private Integer maxPerOrder;

        private Instant saleStartTime;
        private Instant saleEndTime;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSeatSectionRequest {
        @NotBlank(message = "Mã khu vực ghế không được để trống")
        @Size(max = 60)
        private String code;

        @NotBlank(message = "Tên khu vực ghế không được để trống")
        @Size(max = 150)
        private String name;

        @NotNull(message = "Sức chứa khu vực không được để trống")
        @Min(value = 1, message = "Sức chứa khu vực phải >= 1")
        private Integer capacity;

        private Integer sortOrder;
        private UUID assignedTicketTypeId;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateSeatSectionRequest {
        @NotBlank(message = "Mã khu vực ghế không được để trống")
        @Size(max = 60)
        private String code;

        @NotBlank(message = "Tên khu vực ghế không được để trống")
        @Size(max = 150)
        private String name;

        @NotNull(message = "Sức chứa khu vực không được để trống")
        @Min(value = 1, message = "Sức chứa khu vực phải >= 1")
        private Integer capacity;

        private Integer sortOrder;
        private UUID assignedTicketTypeId;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatItemRequest {
        @NotBlank(message = "Hàng ghế không được để trống")
        @Size(max = 20)
        private String rowLabel;

        @NotBlank(message = "Số ghế không được để trống")
        @Size(max = 20)
        private String seatNumber;

        @Size(max = 50)
        private String seatLabel;

        private UUID assignedTicketTypeId;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BulkCreateSeatsRequest {
        @NotEmpty(message = "Danh sách ghế không được để trống")
        @Valid
        private List<SeatItemRequest> seats;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignTicketTypeRequest {
        @NotNull(message = "ticketTypeId không được để trống")
        private UUID ticketTypeId;

        private List<UUID> seatIds;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RejectEventRequest {
        @NotBlank(message = "Lý do từ chối sự kiện không được để trống")
        @Size(max = 1000)
        private String rejectionReason;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CancelEventRequest {
        @NotBlank(message = "Lý do hủy sự kiện không được để trống")
        @Size(max = 1000)
        private String cancellationReason;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketInventoryResponse {
        private UUID id;
        private UUID showTimeId;
        private UUID ticketTypeId;
        private String ticketTypeName;
        private BigDecimal price;
        private Integer totalQuantity;
        private Integer reservedQuantity;
        private Integer soldQuantity;
        private Integer availableQuantity;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShowTimeResponse {
        private UUID id;
        private Instant startTime;
        private Instant endTime;
        private String venueAddress;
        private Integer capacity;
        private ShowTimeStatus status;
        private List<TicketInventoryResponse> inventories;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketTypeResponse {
        private UUID id;
        private String name;
        private String description;
        private BigDecimal price;
        private Integer totalQuantity;
        private Integer maxPerOrder;
        private Instant saleStartTime;
        private Instant saleEndTime;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatResponse {
        private UUID id;
        private UUID sectionId;
        private String rowLabel;
        private String seatNumber;
        private String seatLabel;
        private UUID assignedTicketTypeId;
        private SeatAvailabilityStatus availabilityStatus;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatSectionResponse {
        private UUID id;
        private String code;
        private String name;
        private Integer capacity;
        private Integer sortOrder;
        private UUID assignedTicketTypeId;
        private List<SeatResponse> seats;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventSummaryResponse {
        private UUID id;
        private UUID organizerId;
        private UUID categoryId;
        private String categoryName;
        private String title;
        private String slug;
        private EventStatus status;
        private SeatingMode seatingMode;
        private String bannerUrl;
        private String thumbnailUrl;
        private String venueName;
        private String city;
        private BigDecimal minPrice;
        private Instant nextShowTime;
        private Instant createdAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDetailResponse {
        private UUID id;
        private UUID organizerId;
        private UUID categoryId;
        private String categoryName;
        private String title;
        private String slug;
        private String description;
        private EventStatus status;
        private SeatingMode seatingMode;
        private String bannerUrl;
        private String thumbnailUrl;
        private String venueName;
        private String city;
        private String address;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private RefundPolicy refundPolicy;
        private Integer refundDeadlineHours;
        private BigDecimal refundFeePercent;
        private String rejectionReason;
        private String cancellationReason;
        private Instant submittedAt;
        private Instant publishedAt;
        private Instant cancelledAt;
        private Instant endedAt;
        private List<ShowTimeResponse> showTimes;
        private List<TicketTypeResponse> ticketTypes;
        private List<SeatSectionResponse> seatSections;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
