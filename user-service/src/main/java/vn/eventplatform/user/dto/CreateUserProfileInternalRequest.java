package vn.eventplatform.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserProfileInternalRequest {

    @NotNull(message = "ID không được để trống")
    private UUID id;

    @NotBlank(message = "Email không được để trống")
    @Email
    private String email;

    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullName;

    private String phoneNumber;
    private String avatarUrl;
}
