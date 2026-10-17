package vn.eventplatform.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityPolicyDto {

    @NotNull(message = "Ngưỡng nhập sai mật khẩu tối đa không được để trống")
    @Min(value = 1, message = "Ngưỡng nhập sai tối thiểu là 1 lần")
    @Max(value = 20, message = "Ngưỡng nhập sai tối đa là 20 lần")
    private Integer maxFailedAttempts;

    @NotNull(message = "Thời gian khóa tài khoản (phút) không được để trống")
    @Min(value = 1, message = "Thời gian khóa tối thiểu là 1 phút")
    @Max(value = 10080, message = "Thời gian khóa tối đa là 10080 phút (7 ngày)")
    private Integer lockoutDurationMinutes;

    @Min(value = 1, message = "Thời hạn mã OTP tối thiểu là 1 phút")
    @Max(value = 60, message = "Thời hạn mã OTP tối đa là 60 phút")
    private Integer otpExpirationMinutes;

    @Min(value = 1, message = "Số lần thử OTP tối thiểu là 1 lần")
    @Max(value = 10, message = "Số lần thử OTP tối đa là 10 lần")
    private Integer maxOtpAttempts;

    private UUID updatedBy;
    private Instant updatedAt;
}
