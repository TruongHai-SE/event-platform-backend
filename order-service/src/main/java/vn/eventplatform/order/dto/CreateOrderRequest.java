package vn.eventplatform.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
