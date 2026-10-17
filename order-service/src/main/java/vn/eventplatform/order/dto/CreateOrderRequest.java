package vn.eventplatform.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
public class CreateOrderRequest {

    @NotNull(message = "eventId không được để trống")
    private UUID eventId;

    @NotNull(message = "showTimeId không được để trống")
    private UUID showTimeId;

    @NotEmpty(message = "Danh sách hạng vé đặt mua không được để trống")
    @Valid
    private List<CreateOrderItemRequest> items;

    @NotBlank(message = "Tên người đặt vé không được để trống")
    private String customerName;

    @NotBlank(message = "Email người đặt vé không được để trống")
    @Email(message = "Email người đặt vé không đúng định dạng")
    private String customerEmail;

    private String customerPhone;

    private String promotionCode;

    private UUID promotionId;

    private BigDecimal discountAmount;

    public CreateOrderRequest() {
    }

    public CreateOrderRequest(UUID eventId, UUID showTimeId, List<CreateOrderItemRequest> items,
                              String customerName, String customerEmail, String customerPhone,
                              String promotionCode, UUID promotionId, BigDecimal discountAmount) {
        this.eventId = eventId;
        this.showTimeId = showTimeId;
        this.items = items;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.promotionCode = promotionCode;
        this.promotionId = promotionId;
        this.discountAmount = discountAmount;
    }
}
