package vn.eventplatform.common.dto;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // 400 Bad Request
    BAD_REQUEST("ERR_BAD_REQUEST", "Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("ERR_VALIDATION", "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    ACCOUNT_ALREADY_EXISTS("ERR_ACCOUNT_EXISTS", "Email hoặc tên đăng nhập đã tồn tại", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("ERR_INVALID_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không chính xác", HttpStatus.BAD_REQUEST),
    TOKEN_INVALID("ERR_TOKEN_INVALID", "Mã xác thực không hợp lệ hoặc đã hết hạn", HttpStatus.BAD_REQUEST),
    PASSWORD_NOT_MATCH("ERR_PASSWORD_MISMATCH", "Mật khẩu xác nhận không khớp", HttpStatus.BAD_REQUEST),

    // 401 Unauthorized
    UNAUTHORIZED("ERR_UNAUTHORIZED", "Yêu cầu xác thực tài khoản", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("ERR_TOKEN_EXPIRED", "Phiên đăng nhập đã hết hạn", HttpStatus.UNAUTHORIZED),

    // 403 Forbidden
    ACCESS_DENIED("ERR_ACCESS_DENIED", "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    ACCOUNT_LOCKED("ERR_ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa", HttpStatus.FORBIDDEN),
    ACCOUNT_PENDING_VERIFICATION("ERR_ACCOUNT_PENDING", "Vui lòng kích hoạt tài khoản qua email trước khi đăng nhập", HttpStatus.FORBIDDEN),

    // 404 Not Found
    RESOURCE_NOT_FOUND("ERR_NOT_FOUND", "Không tìm thấy dữ liệu yêu cầu", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND("ERR_USER_NOT_FOUND", "Người dùng không tồn tại", HttpStatus.NOT_FOUND),
    ROLE_NOT_FOUND("ERR_ROLE_NOT_FOUND", "Vai trò không tồn tại", HttpStatus.NOT_FOUND),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR("ERR_INTERNAL_SERVER", "Đã xảy ra lỗi hệ thống, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR),
    MAIL_SENDING_FAILED("ERR_MAIL_SENDING", "Không thể gửi email xác thực, vui lòng thử lại sau", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ErrorCode(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }
}
