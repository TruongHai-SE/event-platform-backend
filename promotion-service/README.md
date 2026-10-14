# Promotion Service

Service quản lý voucher cho nền tảng sự kiện. Local URL: `http://localhost:8085`.

- Swagger UI: `http://localhost:8085/swagger-ui/index.html`
- OpenAPI: `http://localhost:8085/v3/api-docs`

## Luồng dành cho frontend

### 1. Admin quản trị voucher

1. Admin tạo voucher tại `POST /api/v1/admin/vouchers`.
2. Admin xem, sửa hoặc ngừng hoạt động voucher qua API Admin.
3. Nếu cần phát hành riêng cho một người dùng, Admin gọi API `issue`; voucher sẽ xuất hiện trong ví của user.
4. Admin có thể tạo voucher bồi hoàn bằng `isCompensation: true`.

### 2. BTC quản lý voucher sự kiện

1. BTC tạo voucher tại `POST /api/v1/organizer/vouchers`.
2. `organizerId` luôn được lấy từ JWT, frontend không cần và không nên tin vào giá trị này từ form.
3. BTC chỉ đọc/sửa/xóa voucher của chính mình. Voucher của BTC khác trả về `403`.
4. BTC không thể tạo voucher bồi hoàn.

### 3. Hiển thị ví voucher cho user

1. Sau đăng nhập, frontend gọi `GET /api/v1/vouchers/wallet`.
2. Chỉ hiển thị item có `status = RESERVED` là có thể sử dụng.
3. Sau khi order áp dụng voucher thành công, item chuyển `COMPLETED`.
4. Nếu order bị hủy hoặc hết hạn, item được trả về `RESERVED`.

### 4. Áp dụng voucher khi checkout

Frontend không gọi API nội bộ trực tiếp. Luồng đúng là:

1. Frontend gửi voucher code trong request tạo/cập nhật order.
2. `order-service` gọi `POST /internal/v1/vouchers/validate` để hiển thị tiền được giảm.
3. Khi order được xác nhận dùng voucher, `order-service` gọi `POST /internal/v1/vouchers/apply`.
4. Nếu order bị hủy hoặc timeout, `order-service` gọi `POST /internal/v1/vouchers/release`.

Các API nội bộ yêu cầu header `X-Internal-Secret`; không được nhúng secret vào frontend.

## API contract

Tất cả API người dùng cần `Authorization: Bearer <access-token>`.

| Nhóm | Endpoint | Role | Mô tả |
|---|---|---|---|
| Admin | `POST /api/v1/admin/vouchers` | `ROLE_ADMIN` | Tạo voucher toàn hệ thống |
| Admin | `GET /api/v1/admin/vouchers` | `ROLE_ADMIN` | Danh sách toàn bộ voucher |
| Admin | `GET/PUT/DELETE /api/v1/admin/vouchers/{id}` | `ROLE_ADMIN` | Chi tiết, cập nhật, ngừng hoạt động |
| Admin | `POST /api/v1/admin/vouchers/{id}/issue` | `ROLE_ADMIN` | Cấp voucher vào ví user |
| BTC | `POST /api/v1/organizer/vouchers` | `ROLE_ORGANIZER` | Tạo voucher thuộc BTC hiện tại |
| BTC | `GET /api/v1/organizer/vouchers` | `ROLE_ORGANIZER` | Danh sách voucher của BTC |
| BTC | `GET/PUT/DELETE /api/v1/organizer/vouchers/{id}` | `ROLE_ORGANIZER` | CRUD voucher sở hữu |
| User | `GET /api/v1/vouchers/wallet` | Đăng nhập | Xem ví voucher |
| Internal | `POST /internal/v1/vouchers/validate` | Internal secret | Kiểm tra điều kiện và tiền giảm |
| Internal | `POST /internal/v1/vouchers/apply` | Internal secret | Áp dụng voucher cho order |
| Internal | `POST /internal/v1/vouchers/release` | Internal secret | Hoàn voucher/quota |

### Voucher request

```json
{
  "eventId": "b5fd1d4e-b560-4883-aea3-071907c1fb80",
  "code": "FEST10",
  "discountType": "PERCENTAGE",
  "discountValue": 10,
  "minOrderAmount": 200000,
  "maxDiscountAmount": 50000,
  "totalUsageLimit": 100,
  "maxUsagePerUser": 1,
  "validFrom": "2026-10-14T00:00:00",
  "validTo": "2026-12-31T23:59:59",
  "isActive": true
}
```

`eventId = null` nghĩa là voucher áp dụng toàn sàn. Với `PERCENTAGE`, nên truyền `maxDiscountAmount` để giới hạn tiền giảm.

### Internal request examples

```json
// validate
{ "userId": "uuid", "eventId": "uuid-or-null", "code": "FEST10", "orderAmount": 300000 }

// apply
{ "orderId": "uuid", "userId": "uuid", "eventId": "uuid-or-null", "code": "FEST10", "orderAmount": 300000 }

// release
{ "orderId": "uuid", "reason": "REVOKED" }
```

`reason` chỉ nhận `REVOKED` (hủy order) hoặc `EXPIRED` (order hết hạn).

## Business rules

- `FIXED_AMOUNT`: giảm đúng số tiền cấu hình nhưng không vượt giá trị order.
- `PERCENTAGE`: tính theo phần trăm; bị chặn bởi `maxDiscountAmount` nếu có.
- Order phải đạt `minOrderAmount` nếu voucher cấu hình giá trị tối thiểu.
- Voucher phải active, nằm trong khoảng `validFrom`–`validTo`, và đúng event nếu `eventId` không null.
- Một `orderId` chỉ có một lịch sử áp dụng voucher.
- `totalUsageLimit` được tăng/giảm atomic trong database.
- `maxUsagePerUser` được kiểm tra trong transaction khóa theo voucher.
- Release chỉ thực hiện một lần trên usage đang `USED`; thao tác lặp lại không làm giảm quota lần nữa.

## Báo cáo test

Lệnh chạy:

```bash
mvn -pl promotion-service -am test
```

Kết quả gần nhất: **10 tests, 0 failures, 0 errors**. JaCoCo: **84.62% line coverage**, **85.24% instruction coverage**.

| Nhóm test | Case đã kiểm tra |
|---|---|
| Tính tiền | Giảm cố định; không giảm quá giá trị order; phần trăm; cap `maxDiscountAmount` |
| Điều kiện dùng | `eventId`, toàn sàn, đơn tối thiểu, thời gian bắt đầu/kết thúc |
| Quota | Tổng lượt dùng, mỗi user, 8 request đồng thời với quota 3 chỉ có 3 request thành công |
| Lịch sử | Chống áp dụng trùng theo `orderId` |
| Release | `REVOKED`, `EXPIRED`, hoàn quota và trả item ví về `RESERVED` |
| API nội bộ | Validate/apply/release thành công; từ chối `X-Internal-Secret` sai |
| API frontend | CRUD Admin, CRUD BTC, ngăn BTC truy cập voucher không sở hữu, cấp và đọc ví voucher |
| Runtime | Swagger UI và OpenAPI khởi động, HTTP 200; validate request voucher không tồn tại trả HTTP 404 |
