# Event & Festival Management Platform — Backend Microservices

> **Production-Ready Spring Boot 3 & Spring Cloud Microservices Architecture** for the Event & Festival Management Platform (**MSS301**).  
> Built with **100% real data persistence, zero-hardcode mocking**, dedicated **Database-per-Service** isolation on **PostgreSQL 16**, **Flyway Migrations**, **Stateless Dual-Token JWT (HttpOnly Cookie)**, **Netflix Eureka Service Discovery**, **Spring Cloud Gateway**, **OpenFeign IPC**, **Brevo Transactional SMTP**, and **Google OAuth2 Identity Integration**.

---

## Tech Stack & Architecture Badges

<p align="center">
  <a href="https://www.oracle.com/java/"><img src="https://img.shields.io/badge/Java_21_LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring_Boot_3.3.4-6DB33F?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot 3.3.4"></a>
  <a href="https://spring.io/projects/spring-cloud"><img src="https://img.shields.io/badge/Spring_Cloud_2023.0.3-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Cloud"></a>
  <a href="https://spring.io/projects/spring-security"><img src="https://img.shields.io/badge/Spring_Security_6-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white" alt="Spring Security 6"></a>
  <a href="https://jwt.io/"><img src="https://img.shields.io/badge/JJWT_0.12.6-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white" alt="JJWT"></a>
  <a href="https://maven.apache.org/"><img src="https://img.shields.io/badge/Maven_3.9+-C71A36?style=for-the-badge&logo=apachemaven&logoColor=white" alt="Maven"></a>
</p>

<p align="center">
  <a href="https://www.postgresql.org/"><img src="https://img.shields.io/badge/PostgreSQL_16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL 16"></a>
  <a href="https://flywaydb.org/"><img src="https://img.shields.io/badge/Flyway_Migration-CC0200?style=for-the-badge&logo=flyway&logoColor=white" alt="Flyway"></a>
  <a href="https://www.docker.com/"><img src="https://img.shields.io/badge/Docker_Compose-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker Compose"></a>
  <a href="https://cloud.spring.io/spring-cloud-openfeign/"><img src="https://img.shields.io/badge/Spring_Cloud_OpenFeign-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="OpenFeign"></a>
  <a href="https://www.brevo.com/"><img src="https://img.shields.io/badge/Brevo_SMTP-0B996E?style=for-the-badge&logo=brevo&logoColor=white" alt="Brevo"></a>
  <a href="https://developers.google.com/identity"><img src="https://img.shields.io/badge/Google_OAuth2-4285F4?style=for-the-badge&logo=google&logoColor=white" alt="Google OAuth2"></a>
</p>

---

## 1. Technical Overview & Architectural Decisions

| Aspect | Engineering Choice | Rationale |
| :--- | :--- | :--- |
| **Architecture Pattern** | **Microservices Architecture** (Distributed Services) | Các miền nghiệp vụ được phân rã thành các dịch vụ độc lập (`auth-service`, `user-service`), kết hợp `discovery-service` (Eureka) và `api-gateway`, cho phép triển khai, nâng cấp và mở rộng (scale) độc lập. |
| **Database per Service** | Logical Database Isolation (`event_auth_db`, `event_user_db`) | Mỗi microservice sở hữu toàn quyền một Database riêng biệt trong PostgreSQL container. Ngăn chặn tuyệt đối việc join bảng xuyên service hoặc vi phạm ranh giới dữ liệu (bounded context). |
| **Service Discovery** | **Netflix Eureka Server** (`discovery-service:8761`) | Đăng ký động và giám sát nhịp tim (heartbeat) của các instance microservice, loại bỏ việc cấu hình cứng địa chỉ IP/Port giữa các service. |
| **Edge Gateway & Routing** | **Spring Cloud Gateway** (`api-gateway:8080`) | Điểm đón nhận request tập trung (Single Entry Point), đảm nhiệm cân bằng tải client-side (`lb://`), xử lý chính sách CORS toàn cục và định tuyến URL chuẩn RESTful (`/api/v1/auth/**`, `/api/v1/users/**`). |
| **Security & Authentication** | **Dual-Token Pattern** (HMAC-SHA256 JJWT) | `AccessToken` (15 phút) cấp cho client dưới dạng Bearer Token; `RefreshToken` (7 ngày) lưu trong Cookie `HttpOnly, Secure, SameSite=Lax` chống XSS và CSRF. Lưu trữ `token_hash` và hỗ trợ thu hồi (revocation) tại database. |
| **Inter-Service Communication** | **Spring Cloud OpenFeign** + Internal Secret Token | Giao tiếp đồng bộ hướng Declarative REST qua Eureka Service Registry. Mọi endpoint nội bộ (`/internal/v1/**`) được bảo vệ bằng header bí mật `X-Internal-Secret`, ngăn chặn truy cập trái phép từ bên ngoài gateway. |
| **Clean Technical Commons** | **Decoupled `common-base` Library** | Module thư viện dùng chung chỉ chứa các thành phần kỹ thuật hạ tầng (`ApiResponse`, `PageResponse`, `ErrorCode`, `BaseAuditableEntity`, `AppException`, `GlobalExceptionHandler`), **tuyệt đối không chứa Domain Entity hay Business Logic**. |
| **Fault Isolation** | Non-fatal Exception Handling (Graceful Degradation) | Lệnh gọi Feign Client đồng bộ tạo profile sang `user-service` được bọc try-catch độc lập; nếu `user-service` tạm thời downtime, luồng đăng ký tài khoản tại `auth-service` vẫn hoàn tất an toàn và ghi log cảnh báo. |
| **Transactional Email** | Real SMTP via Brevo (`smtp-relay.brevo.com`) | Gửi email kích hoạt tài khoản thật chứa mã xác thực OTP 6 số (hạn 24h) và luồng quên mật khẩu, không dùng dữ liệu giả lập. |
| **Database Migration** | **Flyway Database Migration** | Quản lý vòng đời lược đồ cơ sở dữ liệu có phiên bản (`V1__...`, `V2__...`), tự động tạo bảng UUID v4, index tối ưu và seed dữ liệu chuẩn RBAC khi service khởi động. |

---

## 2. System Architecture

```mermaid
flowchart TB
    subgraph Clients["Client Applications"]
        WebClient["Web Client (React / Vite)"]
        MobileClient["Mobile Application"]
        PostmanClient["API Testing (cURL / Postman)"]
    end

    subgraph EdgeLayer["Edge & Ingress Layer"]
        Gateway["Spring Cloud Gateway (:8080)<br/>• Dynamic Routing (lb://)<br/>• Global CORS Configuration<br/>• Path Prefix Mapping"]
    end

    subgraph ServiceMesh["Service Discovery & Registry"]
        Eureka["Netflix Eureka Server (:8761)<br/>• Instance Heartbeat Monitoring<br/>• Client-Side Load Balancing Registry"]
    end

    subgraph CoreMicroservices["Core Business Microservices"]
        subgraph AuthDomain["auth-service (:8081)"]
            AuthController["AuthenticationController"]
            AuthService["AuthenticationService"]
            TokenProvider["JwtTokenProvider"]
            FeignPort["UserProfilePort (OpenFeign)"]
        end

        subgraph UserDomain["user-service (:8082)"]
            UserController["UserProfileController"]
            RoleController["RolePermissionController"]
            InternalController["InternalUserController"]
            UserService["UserProfileService"]
            TokenFilter["TokenVerificationFilter (Stateless JWT)"]
        end
    end

    subgraph TechnicalCommons["Shared Technical Base"]
        CommonBase["common-base (Library)<br/>• ApiResponse / PageResponse<br/>• ErrorCode & AppException<br/>• BaseAuditableEntity (UUID, Timestamps)<br/>• GlobalExceptionHandler"]
    end

    subgraph PersistenceLayer["Data Persistence (Docker Container: event-platform-postgres)"]
        AuthDB[("event_auth_db<br/>• accounts<br/>• verification_tokens<br/>• refresh_tokens<br/>• audit_logs")]
        UserDB[("event_user_db<br/>• user_profiles<br/>• roles<br/>• permissions<br/>• role_permissions<br/>• user_roles")]
    end

    subgraph ExternalServices["External Infrastructure Providers"]
        BrevoSMTP["Brevo SMTP Service<br/>(Transactional Email Delivery)"]
        GoogleOAuth["Google Identity Services<br/>(OAuth2 Token Verification)"]
    end

    %% Client calls Gateway
    WebClient -->|HTTP /api/v1/**| Gateway
    MobileClient -->|HTTP /api/v1/**| Gateway
    PostmanClient -->|HTTP /api/v1/**| Gateway

    %% Discovery Heartbeats
    Gateway -.->|Resolve Services| Eureka
    AuthDomain -.->|Heartbeat Registration| Eureka
    UserDomain -.->|Heartbeat Registration| Eureka

    %% Gateway Routing
    Gateway -->|/api/v1/auth/**| AuthController
    Gateway -->|/api/v1/users/**, /api/v1/roles/**| UserController

    %% Inter-service Feign IPC
    FeignPort ==>|Internal REST (X-Internal-Secret)<br/>POST /internal/v1/users| InternalController

    %% Commons dependencies
    CommonBase -.->|Maven Dependency| AuthDomain
    CommonBase -.->|Maven Dependency| UserDomain

    %% Database Isolation
    AuthDomain ==>|JDBC URL :5432/event_auth_db| AuthDB
    UserDomain ==>|JDBC URL :5432/event_user_db| UserDB

    %% External APIs
    AuthService -->|SMTP Port 587| BrevoSMTP
    AuthService -->|Token Verify| GoogleOAuth

    classDef gateway fill:#2496ED,stroke:#fff,stroke-width:2px,color:#fff;
    classDef eureka fill:#ED8B00,stroke:#fff,stroke-width:2px,color:#fff;
    classDef service fill:#6DB33F,stroke:#fff,stroke-width:2px,color:#fff;
    classDef db fill:#4169E1,stroke:#fff,stroke-width:2px,color:#fff;
    classDef ext fill:#0B996E,stroke:#fff,stroke-width:2px,color:#fff;
    classDef common fill:#6c757d,stroke:#fff,stroke-width:2px,color:#fff;

    class Gateway gateway;
    class Eureka eureka;
    class AuthController,AuthService,TokenProvider,FeignPort,UserController,RoleController,InternalController,UserService,TokenFilter service;
    class AuthDB,UserDB db;
    class BrevoSMTP,GoogleOAuth ext;
    class CommonBase common;
```

---

## 3. Đánh giá Tiêu chuẩn Kiến trúc Microservices (Architecture Compliance)

Hệ thống được thiết kế và triển khai tuân thủ nghiêm ngặt các nguyên lý của **Microservices Architecture**:

```
+---------------------------------------------------------------------------------------------------------+
|                                    BẢNG KIỂM TRA ĐẠT CHUẨN KIẾN TRÚC                                    |
+----+-----------------------------------------------------+------------+---------------------------------+
| STT| Tiêu chí Kiểm định                                  | Trạng thái | Minh chứng kỹ thuật thực tế     |
+----+-----------------------------------------------------+------------+---------------------------------+
| 1  | Auth và User chạy thành 2 ứng dụng riêng, deploy riêng  |  ĐẠT 100%  | pom.xml riêng, port riêng, jar  |
| 2  | Mỗi service chỉ truy cập database của mình          |  ĐẠT 100%  | event_auth_db & event_user_db   |
| 3  | Trao đổi qua API/Sự kiện, không gọi chéo code/repo  |  ĐẠT 100%  | OpenFeign, Stateless JWT Filter |
| 4  | common-base chỉ chứa kỹ thuật, không chứa nghiệp vụ |  ĐẠT 100%  | Base DTO/Exception/MappedSuper  |
| 5  | Có cơ chế xử lý lỗi khi gọi liên service             |  ĐẠT TỐT   | Try-catch non-fatal & ErrorLog  |
+----+-----------------------------------------------------+------------+---------------------------------+
```

### 3.1. Tiêu chí 1: Hai Ứng dụng Riêng biệt — Độc lập Triển khai (Independent Deployability)
* `auth-service` và `user-service` là 2 Maven module độc lập, sở hữu `pom.xml`, packaging `jar`, và file khởi chạy `@SpringBootApplication` hoàn toàn tách biệt:
  * [AuthServiceApplication.java](file:///d:/MSS301/MSS301_Project/auth-service/src/main/java/vn/eventplatform/auth/AuthServiceApplication.java) (Port `8081`).
  * [UserServiceApplication.java](file:///d:/MSS301/MSS301_Project/user-service/src/main/java/vn/eventplatform/user/UserServiceApplication.java) (Port `8082`).
* Đóng gói thông qua `spring-boot-maven-plugin`. Có thể triển khai trên 2 máy chủ ảo, 2 container Docker hoặc 2 Kubernetes Pods riêng biệt mà không ảnh hưởng lẫn nhau.

### 3.2. Tiêu chí 2: Cô lập Cơ sở Dữ liệu (Database-per-Service Pattern)
* `auth-service` chỉ kết nối tới `jdbc:postgresql://localhost:5432/event_auth_db`. Lưu trữ các bảng xác thực: `accounts`, `verification_tokens`, `refresh_tokens`, `audit_logs`.
* `user-service` chỉ kết nối tới `jdbc:postgresql://localhost:5432/event_user_db`. Lưu trữ các bảng hồ sơ: `user_profiles`, `roles`, `permissions`, `role_permissions`, `user_roles`.
* Hai service chạy trên cùng 1 PostgreSQL Docker container nhằm tối ưu RAM/CPU cho môi trường Dev, nhưng **hoàn toàn cách ly về mặt logic database**. Không có kết nối chéo schema hay quyền truy cập bảng của nhau.
* Lược đồ và dữ liệu mẫu được quản lý riêng qua Flyway Migrations ở từng service.

### 3.3. Tiêu chí 3: Giao tiếp Hướng API Loose Coupling (Decoupled Inter-Service IPC)
* **Về mã nguồn (Code Level):** `auth-service` không khai báo dependency tới `user-service` trong `pom.xml` và ngược lại. Do đó không thể import class, gọi Service hay can thiệp Repository của nhau.
* **Về giao tiếp mạng:** 
  * Khi tài khoản đăng ký mới, `auth-service` kích hoạt lệnh đồng bộ sang `user-service` qua interface khai báo **Spring Cloud OpenFeign** ([UserProfilePort.java](file:///d:/MSS301/MSS301_Project/auth-service/src/main/java/vn/eventplatform/auth/client/UserProfilePort.java)) tới endpoint `POST /internal/v1/users`, xác thực bằng secret header `X-Internal-Secret`.
  * Khi client gọi tới `user-service` (ví dụ `GET /api/v1/users/me`), [TokenVerificationFilter.java](file:///d:/MSS301/MSS301_Project/user-service/src/main/java/vn/eventplatform/user/security/TokenVerificationFilter.java) tự giải mã và xác thực chữ ký JWT bằng Secret Key dùng chung, **không cần gọi ngược lại `auth-service`**, loại bỏ nút thắt cổ chai mạng và điểm nghẽn chịu lỗi (single-point-of-failure).

### 3.4. Tiêu chí 4: Thư viện Kỹ thuật Dùng chung Tinh gọn (Clean `common-base`)
* Module [common-base](file:///d:/MSS301/MSS301_Project/common-base) chỉ bao gồm hạ tầng kỹ thuật cross-cutting:
  * `vn.eventplatform.common.dto`: `ApiResponse<T>`, `PageResponse<T>`, `ErrorCode`.
  * `vn.eventplatform.common.exception`: `AppException`, `GlobalExceptionHandler`.
  * `vn.eventplatform.common.entity`: `BaseAuditableEntity` (chỉ là abstract `@MappedSuperclass` cung cấp `id`, `createdAt`, `updatedAt`, `isDeleted`).
* **Không chứa bất kỳ Domain Entity nghiệp vụ** (`Account`, `UserProfile`, `Event`, `Ticket`...), không chứa DAO/Repository, bảo toàn nguyên lý Domain-Driven Design (DDD).

### 3.5. Tiêu chí 5: Cô lập và Xử lý Lỗi Liên Dịch vụ (Fault Tolerance & Resilience)
* Tại [AuthenticationService.java](file:///d:/MSS301/MSS301_Project/auth-service/src/main/java/vn/eventplatform/auth/service/AuthenticationService.java#L70-L75), tiến trình Feign IPC sang `user-service` được bọc trong khối `try - catch (Exception e)` độc lập.
* Nếu `user-service` bị sập, khởi động chậm hoặc timeout mạng, exception được catch và ghi nhật ký cảnh báo (`log.error`), tiến trình đăng ký và gửi email OTP của `auth-service` vẫn hoàn tất bình thường (**Graceful Degradation**).
* Khi người dùng kích hoạt tài khoản hoặc đăng nhập thành công, hệ thống hỗ trợ cơ chế nạp bù profile nếu chưa tồn tại.

---

## 4. Cấu trúc Dự án (Repository Structure)

```
MSS301_Project/
├── docker/
│   └── init-multi-db.sh                # Script Bash tự động khởi tạo event_auth_db & event_user_db
├── docker-compose.yml                  # PostgreSQL 16 Alpine container (Port 5432)
├── .env                                # Cấu hình biến môi trường thực tế (DB, JWT, Brevo, OAuth)
├── .env.example                        # Bản mẫu cấu hình cho các lập trình viên
├── pom.xml                             # Maven Parent POM (Java 21, Spring Boot 3.3.4, Spring Cloud 2023.0.3)
│
├── common-base/                        # Thư viện kỹ thuật dùng chung
│   └── src/main/java/vn/eventplatform/common/
│       ├── dto/                        # ApiResponse, PageResponse, ErrorCode
│       ├── entity/                     # BaseAuditableEntity (UUID, timestamps, soft-delete)
│       └── exception/                  # AppException, GlobalExceptionHandler
│
├── discovery-service/                  # Netflix Eureka Server (Port 8761)
│   └── src/main/resources/application.yml
│
├── api-gateway/                        # Spring Cloud Gateway (Port 8080)
│   └── src/main/resources/application.yml # Định tuyến động lb:// và cấu hình CORS
│
├── auth-service/                       # Dịch vụ Quản lý Định danh & Bảo mật (Port 8081)
│   ├── src/main/java/vn/eventplatform/auth/
│   │   ├── client/                     # OpenFeign client gọi sang user-service
│   │   ├── controller/                 # AuthenticationController
│   │   ├── entity/                     # Account, VerificationToken, RefreshToken, AuditLog
│   │   ├── repository/                 # AccountRepository, TokenRepository
│   │   ├── security/                   # JwtTokenProvider, SecurityConfiguration
│   │   └── service/                    # AuthenticationService, Brevo NotificationSender
│   └── src/main/resources/db/migration/
│       └── V1__init_auth_schema.sql    # Schema bảng accounts, verification_tokens, refresh_tokens
│
└── user-service/                       # Dịch vụ Quản lý Hồ sơ & Phân quyền RBAC (Port 8082)
    ├── src/main/java/vn/eventplatform/user/
    │   ├── controller/                 # UserProfileController, RolePermissionController, InternalUserController
    │   ├── domain/                     # UserProfile, Role, Permission, OrganizerProfile
    │   ├── repository/                 # UserProfileRepository, RoleRepository, PermissionRepository
    │   ├── security/                   # Stateless TokenVerificationFilter
    │   └── service/                    # UserProfileService, RolePermissionService
    └── src/main/resources/db/migration/
        ├── V1__init_user_schema.sql    # Schema bảng user_profiles, roles, permissions
        └── V2__seed_rbac_and_admin.sql # Dữ liệu mẫu vai trò chuẩn (ADMIN, ORGANIZER, ATTENDEE...)
```

---

## 5. Tài liệu API & Centralized Swagger UI (OpenAPI 3)

Hệ thống áp dụng chuẩn **Aggregated Swagger UI tại API Gateway**. Lập trình viên, tester và frontend **không cần nhớ port riêng của từng service**:

* **Cổng Swagger UI Tập trung duy nhất:** 👉 **`http://localhost:8080/swagger-ui.html`**
* **Menu Dropdown "Select a definition" (góc trên bên phải):**
  * `1. Auth Service (Định danh & Xác thực)`: Tra cứu toàn bộ schema và test API đăng ký, đăng nhập, OTP, token kép.
  * `2. User Service (Hồ sơ & Phân quyền)`: Tra cứu toàn bộ API Profile, vai trò RBAC, quyền hạn.
* **Nút "Authorize" (Bearer Token):** Nhập `Bearer <ACCESS_TOKEN>` một lần duy nhất để test trực tiếp tất cả các API được bảo vệ ngay trên trình duyệt mà không cần chuyển cổng.

---

## 6. Danh mục API Endpoints (API Catalog)

Tất cả các API công khai đều được gửi qua **API Gateway tại cổng `8080`**:

### 6.1. Authentication Service (`/api/v1/auth`)
| Phương thức | Đường dẫn API | Quyền truy cập | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Public | Đăng ký tài khoản mới, đồng bộ hồ sơ & gửi email OTP Brevo |
| `POST` | `/api/v1/auth/verify-email` | Public | Kích hoạt tài khoản bằng mã OTP 6 số |
| `POST` | `/api/v1/auth/resend-verification` | Public | Gửi lại mã kích hoạt tài khoản mới qua email |
| `POST` | `/api/v1/auth/login` | Public | Đăng nhập; nhận Access Token (body) & Refresh Token (Cookie) |
| `POST` | `/api/v1/auth/oauth2/google` | Public | Đăng nhập/Đăng ký một chạm bằng Google ID Token |
| `POST` | `/api/v1/auth/refresh-token` | Public | Cấp mới Access Token từ Refresh Token hợp lệ |
| `POST` | `/api/v1/auth/logout` | Authenticated | Đăng xuất, thu hồi Refresh Token và xóa Cookie |
| `POST` | `/api/v1/auth/forgot-password` | Public | Yêu cầu liên kết đặt lại mật khẩu qua email |
| `POST` | `/api/v1/auth/reset-password` | Public | Xác nhận đổi mật khẩu bằng token hợp lệ |

### 5.2. User Profile Service (`/api/v1/users`)
| Phương thức | Đường dẫn API | Quyền truy cập | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/users/me` | Authenticated | Lấy thông tin hồ sơ của tài khoản đang đăng nhập |
| `PUT` | `/api/v1/users/me` | Authenticated | Cập nhật họ tên, số điện thoại, tiểu sử, địa chỉ, ảnh đại diện |
| `GET` | `/api/v1/users` | `ROLE_ADMIN` | Tra cứu, tìm kiếm danh sách người dùng (Phân trang chuẩn) |
| `GET` | `/api/v1/users/{id}` | `ROLE_ADMIN` | Xem chi tiết hồ sơ người dùng theo UUID |
| `PUT` | `/api/v1/users/{id}/roles` | `ROLE_ADMIN` | Gán hoặc thay đổi vai trò quyền hạn của người dùng |

### 5.3. Role & Permission Management (`/api/v1`)
| Phương thức | Đường dẫn API | Quyền truy cập | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/roles` | `ROLE_ADMIN` | Lấy danh sách các vai trò hệ thống có phân trang |
| `GET` | `/api/v1/roles/{id}` | `ROLE_ADMIN` | Xem chi tiết vai trò và danh sách quyền hạn đi kèm |
| `GET` | `/api/v1/permissions` | `ROLE_ADMIN` | Lấy danh sách toàn bộ các quyền hạn hệ thống |

### 5.4. Internal Inter-Service Endpoints (`/internal/v1`)
| Phương thức | Đường dẫn API | Header Bảo mật | Mô tả chức năng |
| :--- | :--- | :--- | :--- |
| `POST` | `/internal/v1/users` | `X-Internal-Secret` | Endpoint nội bộ để `auth-service` tạo profile ban đầu |
| `GET` | `/internal/v1/users/{id}` | `X-Internal-Secret` | Endpoint nội bộ kiểm tra dữ liệu hồ sơ |

---

## 6. Kiến trúc Cơ sở Dữ liệu & Flyway Migrations

Cơ sở dữ liệu sử dụng **PostgreSQL 16** với các khóa chính chuẩn `UUID v4` (`uuid-ossp`) giúp chống lại các cuộc tấn công dự đoán ID (enumeration attack) và hỗ trợ phân tán dữ liệu:

### 6.1. Database `event_auth_db` (`auth-service`)
* **`accounts`**: Lưu trữ định danh tài khoản (`id`, `email`, `username`, `password_hash`, `status`, `failed_login_attempts`, `lockout_until`).
* **`verification_tokens`**: Quản lý mã OTP 6 số xác thực email và token đặt lại mật khẩu với thời hạn cụ thể (`expires_at`, `is_used`).
* **`refresh_tokens`**: Lưu trữ hash của Refresh Token, ràng buộc client IP, User-Agent và cờ thu hồi `is_revoked`.
* **`audit_logs`**: Nhật ký kiểm toán bảo mật các hành vi đăng nhập, đổi mật khẩu và truy cập nhạy cảm.

### 6.2. Database `event_user_db` (`user-service`)
* **`user_profiles`**: Thông tin cá nhân (`id` khớp với `accounts.id`, `email`, `username`, `full_name`, `phone_number`, `avatar_url`, `bio`, `address`).
* **`roles`**: Danh mục vai trò (`ROLE_ADMIN`, `ROLE_ORGANIZER`, `ROLE_ATTENDEE`, `ROLE_STAFF`, `ROLE_VENDOR`).
* **`permissions`**: Danh mục đặc quyền thao tác (`event:create`, `ticket:checkin`, `user:manage`...).
* **`role_permissions` & `user_roles`**: Các bảng liên kết n-n chuẩn hóa RBAC.

---

## 7. Hướng dẫn Cài đặt & Khởi chạy Hệ thống

### Điều kiện tiên quyết (Prerequisites)
* **Java:** JDK 21 LTS hoặc mới hơn.
* **Build Tool:** Apache Maven 3.9+ (hoặc dùng `./mvnw` / `mvnw.cmd`).
* **Docker:** Docker Desktop hoặc Docker Engine + Docker Compose.

### Bước 1: Khởi động Cụm Cơ sở Dữ liệu qua Docker Compose
Tại thư mục gốc của dự án:
```bash
docker compose up -d
```
Kiểm tra trạng thái container:
```bash
docker compose ps
```
Container `event-platform-postgres` chạy trên cổng `5432`, tự động chạy script `init-multi-db.sh` khởi tạo sẵn 2 database `event_auth_db` và `event_user_db`.

### Bước 2: Thiết lập Biến môi trường (.env)
Sao chép cấu hình mẫu từ `.env.example`:
```bash
cp .env.example .env
```
Kiểm tra các thông số trong `.env`:
* `DB_HOST=localhost`, `DB_PORT=5432`, `DB_USERNAME=postgres`, `DB_PASSWORD=postgres`
* `TOKEN_SECRET_KEY`: Khóa bí mật tối thiểu 256-bit (64 ký tự hex)
* `BREVO_SMTP_USER`, `BREVO_SMTP_PASSWORD`: Tài khoản Brevo SMTP gửi email thật
* `GOOGLE_CLIENT_ID`: Khóa Google OAuth Client ID

### Bước 3: Biên dịch toàn bộ dự án
```bash
mvn clean compile -DskipTests
```

### Bước 4: Khởi động các Microservices theo thứ tự chuẩn
Mở các cửa sổ Terminal riêng biệt để khởi động từng service:

1. **Discovery Service (Eureka Server):**
   ```bash
   mvn -pl :discovery-service spring-boot:run
   ```
   *Kiểm tra Dashboard Eureka tại:* [http://localhost:8761](http://localhost:8761)

2. **API Gateway (Edge Router):**
   ```bash
   mvn -pl :api-gateway spring-boot:run
   ```
   *Cổng Gateway mở tại:* [http://localhost:8080](http://localhost:8080)

3. **User Service:**
   ```bash
   mvn -pl :user-service spring-boot:run
   ```
   *Khởi chạy trên port 8082, Flyway tự động nạp V1, V2 schema và seed roles.*

4. **Auth Service:**
   ```bash
   mvn -pl :auth-service spring-boot:run
   ```
   *Khởi chạy trên port 8081, Flyway tự động nạp V1 auth schema.*

---

## 8. Hướng dẫn Kiểm thử Dòng Nghiệp vụ (End-to-End Verification)

### 8.1. Đăng ký tài khoản (Gửi email Brevo thật & Tạo profile qua Feign)
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user.demo@gmail.com",
    "username": "event_attendee_01",
    "password": "Password123@",
    "fullName": "Nguyễn Văn Demo",
    "phoneNumber": "0988776655"
  }'
```
*Hệ thống sẽ trả về mã HTTP 200, tạo account tại `event_auth_db`, đồng bộ profile sang `event_user_db` qua Feign và gửi email chứa mã OTP 6 số qua Brevo.*

### 8.2. Kích hoạt tài khoản bằng mã OTP
```bash
curl -X POST http://localhost:8080/api/v1/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{
    "token": "123456"
  }'
```

### 8.3. Đăng nhập (Nhận Access Token & Cookie Refresh Token)
```bash
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "credential": "event_attendee_01",
    "password": "Password123@"
  }'
```
*Header trả về: `Set-Cookie: refreshToken=...; Path=/api/v1/auth; HttpOnly; SameSite=Lax`*  
*Body trả về:*
```json
{
  "code": 200,
  "message": "Đăng nhập thành công",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 900
  }
}
```

### 8.4. Xem hồ sơ cá nhân (Qua API Gateway)
```bash
curl -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <ACCESS_TOKEN>"
```

### 8.5. Quản trị viên tra cứu danh sách người dùng (Phân trang chuẩn)
```bash
curl -X GET "http://localhost:8080/api/v1/users?page=0&size=10" \
  -H "Authorization: Bearer <ACCESS_TOKEN_ADMIN>"
```

---

## 9. Định hướng Nâng cấp Resilience & Sẵn sàng Production

Để sẵn sàng cho hệ thống chịu tải cao và quy mô lớn trong tương lai, kiến trúc được chuẩn bị sẵn sàng mở rộng các thành phần sau:
* **Circuit Breaker & Fallback:** Bổ sung `resilience4j-spring-boot3` cho OpenFeign để tự động kích hoạt Circuit Breaker, ngắt mạch khi tỷ lệ lỗi vượt ngưỡng và kích hoạt Fallback Factory.
* **Event-Driven Architecture (EDA):** Tích hợp **RabbitMQ** hoặc **Apache Kafka** để chuyển đổi đồng bộ Feign Client sang mô hình xuất bản sự kiện bất đồng bộ (`UserRegisteredEvent`), áp dụng **Transactional Outbox Pattern** đảm bảo tính nhất quán cuối cùng (*Eventual Consistency*).
* **Distributed Tracing & Metrics:** Kích hoạt **Micrometer Tracing + Zipkin/Jaeger** và **Prometheus + Grafana** để giám sát độ trễ và luồng di chuyển của request xuyên suốt qua Gateway, Eureka và các Microservices.

---
*Tài liệu kiến trúc hệ thống phục vụ đồ án môn học MSS301 - Microservices with Spring Boot & Spring Cloud.*
