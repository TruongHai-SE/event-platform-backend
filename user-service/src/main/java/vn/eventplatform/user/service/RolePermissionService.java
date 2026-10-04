package vn.eventplatform.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.dto.PageResponse;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.user.domain.Permission;
import vn.eventplatform.user.domain.Role;
import vn.eventplatform.user.dto.PermissionResponse;
import vn.eventplatform.user.dto.RoleResponse;
import vn.eventplatform.user.repository.PermissionRepository;
import vn.eventplatform.user.repository.RoleRepository;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public PageResponse<RoleResponse> getAllRoles(Pageable pageable) {
        Page<Role> page = roleRepository.findAll(pageable);
        return PageResponse.from(page.map(this::mapToRoleResponse));
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND, "Không tìm thấy vai trò"));
        return mapToRoleResponse(role);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(p -> PermissionResponse.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .build())
                .toList();
    }

    private RoleResponse mapToRoleResponse(Role role) {
        List<String> permissions = role.getPermissions() != null
                ? role.getPermissions().stream().map(Permission::getName).sorted().toList()
                : Collections.emptyList();

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(permissions)
                .build();
    }
}
