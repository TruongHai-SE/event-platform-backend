package vn.eventplatform.auth.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    @Builder.Default
    private long expiresIn = 900; // 15 mins (in seconds)

    private UserSummary user;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSummary {
        private UUID id;
        private String email;
        private String username;
        private String fullName;
        private List<String> roles;
    }
}
