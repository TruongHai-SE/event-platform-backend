package vn.eventplatform.user.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignRolesRequest {

    @NotEmpty(message = "Danh sách vai trò không được để trống")
    private List<String> roleNames;
}
