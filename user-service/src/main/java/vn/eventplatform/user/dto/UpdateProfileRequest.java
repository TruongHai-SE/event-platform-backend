package vn.eventplatform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ và tên phải từ 2 đến 100 ký tự")
    private String fullName;

    @Pattern(
            regexp = "^(\\+84|84|0)[35789][0-9]{8}$",
            message = "Số điện thoại Việt Nam không hợp lệ"
    )
    private String phoneNumber;

    @Size(max = 2000, message = "Tiểu sử không được vượt quá 2000 ký tự")
    private String bio;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @Size(max = 500, message = "URL ảnh đại diện không được vượt quá 500 ký tự")
    @Pattern(
            regexp = "^https?://.+$",
            message = "URL ảnh đại diện phải bắt đầu bằng http:// hoặc https://"
    )
    private String avatarUrl;
}
