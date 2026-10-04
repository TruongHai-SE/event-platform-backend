package vn.eventplatform.auth.client;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private UUID id;
    private String email;
    private String username;
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
    private List<String> roles;
}
