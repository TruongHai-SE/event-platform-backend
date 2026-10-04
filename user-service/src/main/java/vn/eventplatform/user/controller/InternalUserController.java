package vn.eventplatform.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.user.dto.CreateUserProfileInternalRequest;
import vn.eventplatform.user.dto.UserProfileResponse;
import vn.eventplatform.user.service.UserProfileService;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserProfileService userProfileService;

    @Value("${security.internal.secret}")
    private String internalSecret;

    @PostMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> createProfile(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @Valid @RequestBody CreateUserProfileInternalRequest request
    ) {
        validateInternalSecret(secret);
        UserProfileResponse data = userProfileService.createProfile(request);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfileById(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable("id") UUID id
    ) {
        validateInternalSecret(secret);
        UserProfileResponse data = userProfileService.getProfileById(id);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    private void validateInternalSecret(String secret) {
        if (secret == null || !secret.equals(internalSecret)) {
            throw new AppException(ErrorCode.ACCESS_DENIED, "Truy cập nội bộ không hợp lệ");
        }
    }
}
