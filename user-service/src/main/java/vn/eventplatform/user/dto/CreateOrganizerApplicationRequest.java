package vn.eventplatform.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import vn.eventplatform.user.domain.OrganizationType;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrganizerApplicationRequest {

    @NotBlank(message = "Tên tổ chức/ban tổ chức không được để trống")
    @Size(min = 3, max = 200, message = "Tên tổ chức phải từ 3 đến 200 ký tự")
    private String organizationName;

    @NotNull(message = "Loại hình tổ chức không được để trống")
    @Builder.Default
    private OrganizationType organizationType = OrganizationType.INDIVIDUAL;

    @NotBlank(message = "Tên người đại diện không được để trống")
    @Size(min = 2, max = 150, message = "Tên người đại diện phải từ 2 đến 150 ký tự")
    private String representativeName;

    @NotBlank(message = "Email liên hệ không được để trống")
    @Email(message = "Email liên hệ không hợp lệ")
    @Size(max = 150, message = "Email liên hệ không được vượt quá 150 ký tự")
    private String contactEmail;

    @NotBlank(message = "Số điện thoại liên hệ không được để trống")
    @Pattern(
            regexp = "^(\\+84|84|0)[35789][0-9]{8}$",
            message = "Số điện thoại liên hệ Việt Nam không hợp lệ"
    )
    private String contactPhone;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @Size(max = 500, message = "Website URL không được vượt quá 500 ký tự")
    @Pattern(
            regexp = "^https?://.+$",
            message = "Website URL phải bắt đầu bằng http:// hoặc https://"
    )
    private String websiteUrl;

    @NotBlank(message = "Mô tả giới thiệu và kế hoạch tổ chức sự kiện không được để trống")
    @Size(min = 10, max = 2000, message = "Mô tả giới thiệu phải từ 10 đến 2000 ký tự")
    private String bio;
}
