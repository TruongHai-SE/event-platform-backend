package vn.eventplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleOAuthRequest {

    @NotBlank(message = "Google ID Token không được để trống")
    private String idToken;
}
