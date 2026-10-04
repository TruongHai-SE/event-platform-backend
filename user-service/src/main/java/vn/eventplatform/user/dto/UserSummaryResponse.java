package vn.eventplatform.user.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryResponse {
    private UUID id;
    private String email;
    private String username;
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
    private List<String> roles;
    private Instant createdAt;
}
