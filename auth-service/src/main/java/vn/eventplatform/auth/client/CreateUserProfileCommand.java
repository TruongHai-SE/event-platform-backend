package vn.eventplatform.auth.client;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserProfileCommand {
    private UUID id;
    private String email;
    private String username;
    private String fullName;
    private String phoneNumber;
    private String avatarUrl;
}
