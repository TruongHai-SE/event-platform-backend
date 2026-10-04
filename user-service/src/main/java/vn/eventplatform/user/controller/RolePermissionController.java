package vn.eventplatform.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.eventplatform.common.dto.ApiResponse;
import vn.eventplatform.common.dto.PageResponse;
import vn.eventplatform.user.dto.PermissionResponse;
import vn.eventplatform.user.dto.RoleResponse;
import vn.eventplatform.user.service.RolePermissionService;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RolePermissionController {

    private final RolePermissionService rolePermissionService;

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<PageResponse<RoleResponse>>> getAllRoles(
            @PageableDefault(page = 0, size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        PageResponse<RoleResponse> data = rolePermissionService.getAllRoles(pageable);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/roles/{id}")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable("id") Long id) {
        RoleResponse data = rolePermissionService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    @GetMapping("/permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        List<PermissionResponse> data = rolePermissionService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
