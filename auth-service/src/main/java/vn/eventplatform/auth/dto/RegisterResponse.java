package vn.eventplatform.auth.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {
    private UUID accountId;
    private String email;
    private String username;
    private String message;
}
