package vn.eventplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Tài khoản (email hoặc username) không được để trống")
    private String credential;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}
