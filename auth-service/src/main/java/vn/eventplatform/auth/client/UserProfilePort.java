package vn.eventplatform.auth.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserProfilePort {

    @PostMapping("/internal/v1/users")
    ApiResponse<UserProfileDto> createProfile(
            @RequestHeader("X-Internal-Secret") String secret,
            @RequestBody CreateUserProfileCommand command
    );

    @GetMapping("/internal/v1/users/{id}")
    ApiResponse<UserProfileDto> getProfileById(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable("id") UUID id
    );
}
