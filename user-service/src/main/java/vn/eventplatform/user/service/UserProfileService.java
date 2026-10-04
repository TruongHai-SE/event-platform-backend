package vn.eventplatform.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.dto.PageResponse;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.user.domain.Role;
import vn.eventplatform.user.domain.UserProfile;
import vn.eventplatform.user.dto.*;
import vn.eventplatform.user.repository.RoleRepository;
import vn.eventplatform.user.repository.UserProfileRepository;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final RoleRepository roleRepository;

    @Transactional
    public UserProfileResponse createProfile(CreateUserProfileInternalRequest request) {
        if (userProfileRepository.existsById(request.getId())) {
            return getProfileById(request.getId());
        }

        Role defaultRole = roleRepository.findByName("ROLE_ATTENDEE")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_ATTENDEE")
                        .description("Khách tham gia sự kiện")
                        .build()));

        Set<Role> roles = new HashSet<>();
        roles.add(defaultRole);

        UserProfile profile = UserProfile.builder()
                .id(request.getId())
                .email(request.getEmail())
                .username(request.getUsername())
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .avatarUrl(request.getAvatarUrl())
                .roles(roles)
                .build();

        profile = userProfileRepository.save(profile);
        return mapToResponse(profile);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfileById(UUID id) {
        UserProfile profile = userProfileRepository.findByIdWithRoles(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy hồ sơ người dùng"));
        return mapToResponse(profile);
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID id, UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy hồ sơ người dùng"));

        profile.setFullName(request.getFullName());
        if (request.getPhoneNumber() != null) profile.setPhoneNumber(request.getPhoneNumber());
        if (request.getBio() != null) profile.setBio(request.getBio());
        if (request.getAddress() != null) profile.setAddress(request.getAddress());
        if (request.getAvatarUrl() != null) profile.setAvatarUrl(request.getAvatarUrl());

        profile = userProfileRepository.save(profile);
        return mapToResponse(profile);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> searchUsers(String keyword, Pageable pageable) {
        Page<UserProfile> page = userProfileRepository.searchUsers(keyword, pageable);
        return PageResponse.from(page.map(this::mapToSummary));
    }

    @Transactional
    public UserProfileResponse assignRoles(UUID userId, List<String> roleNames) {
        UserProfile profile = userProfileRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "Không tìm thấy hồ sơ người dùng"));

        Set<Role> newRoles = new HashSet<>();
        for (String roleName : roleNames) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND, "Vai trò không hợp lệ: " + roleName));
            newRoles.add(role);
        }

        profile.setRoles(newRoles);
        profile = userProfileRepository.save(profile);
        return mapToResponse(profile);
    }

    private UserProfileResponse mapToResponse(UserProfile p) {
        List<String> roleNames = p.getRoles() != null
                ? p.getRoles().stream().map(Role::getName).sorted().toList()
                : Collections.emptyList();

        return UserProfileResponse.builder()
                .id(p.getId())
                .email(p.getEmail())
                .username(p.getUsername())
                .fullName(p.getFullName())
                .phoneNumber(p.getPhoneNumber())
                .avatarUrl(p.getAvatarUrl())
                .bio(p.getBio())
                .address(p.getAddress())
                .roles(roleNames)
                .createdAt(p.getCreatedAt())
                .build();
    }

    private UserSummaryResponse mapToSummary(UserProfile p) {
        List<String> roleNames = p.getRoles() != null
                ? p.getRoles().stream().map(Role::getName).sorted().toList()
                : Collections.emptyList();

        return UserSummaryResponse.builder()
                .id(p.getId())
                .email(p.getEmail())
                .username(p.getUsername())
                .fullName(p.getFullName())
                .phoneNumber(p.getPhoneNumber())
                .avatarUrl(p.getAvatarUrl())
                .roles(roleNames)
                .createdAt(p.getCreatedAt())
                .build();
    }
}
