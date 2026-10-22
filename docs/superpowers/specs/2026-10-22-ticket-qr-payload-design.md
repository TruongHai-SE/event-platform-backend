# Design Specification: Ticket QR Payload & Generation

## 1. Mục tiêu
Thiết kế và cài đặt cơ chế sinh mã QR payload cho vé điện tử (`Ticket`) trong `ticket-service` với các yêu cầu:
- QR payload chứa đầy đủ thông tin: định danh vé (`ticketId`), sự kiện (`eventId`), suất diễn (`showTimeId`), hạng vé (`ticketTypeId` & `ticketTypeName`), đơn hàng (`orderId`) và số ghế (`seatNumber` nếu có).
- Chuỗi QR payload được đóng gói dạng compact token ký số an toàn bằng thuật toán HMAC-SHA256 (JJWT) chống làm giả hoặc sửa đổi thông tin.
- Tích hợp thư viện Google ZXing để render chuỗi QR payload thành hình ảnh mã QR (PNG) phục vụ hiển thị trực tiếp trên giao diện và tải về.
- Xác thực chữ ký số HMAC-SHA256 trong luồng soát vé tại cổng (`CheckInService`) trước khi kiểm tra trạng thái vé trong database.

## 2. Thư viện & Cấu hình
- Thư viện JWT: `io.jsonwebtoken` (JJWT 0.12.6, đã có trong `ticket-service/pom.xml`).
- Thư viện QR Code: `com.google.zxing:core:3.5.3` và `com.google.zxing:javase:3.5.3`.
- Cấu hình: Khóa bí mật ký QR `security.ticket.qr-secret` (mặc định lấy hoặc fallback sang `security.token.secret-key`).

## 3. Cấu trúc Token Claims (HMAC-SHA256)
Token JWT ký số sử dụng header `{"alg":"HS256","typ":"JWT"}` và payload:
```json
{
  "sub": "550e8400-e29b-41d4-a716-446655440000",
  "eid": "8f8b3c12-3456-4c78-9a01-23456789abcd",
  "sid": "7e7a2b01-1234-4b56-8901-123456789abc",
  "ttid": "6d6c1a90-abcd-4e12-7890-abcdef123456",
  "ttn": "VIP",
  "oid": "1c2d3e4f-5678-90ab-cdef-1234567890ab",
  "seat": "A12",
  "iat": 1729614000
}
```

## 4. Các Thành Phần Xử Lý (Components)
1. **`TicketQrPayloadDto`**:
   - `UUID ticketId`
   - `UUID eventId`
   - `UUID showTimeId`
   - `UUID ticketTypeId`
   - `String ticketTypeName`
   - `UUID orderId`
   - `String seatNumber`
   - `Instant issuedAt`

2. **`TicketQrPayloadService`**:
   - `String generatePayload(TicketQrPayloadDto payload)`: Sinh chuỗi JWT ký số HMAC-SHA256.
   - `TicketQrPayloadDto verifyAndParsePayload(String qrPayload)`: Kiểm tra chữ ký HMAC-SHA256 và bóc tách các trường dữ liệu. Ném `AppException(ErrorCode.BAD_REQUEST)` nếu token không hợp lệ hoặc chữ ký giả mạo.

3. **`QrCodeGeneratorService`**:
   - `byte[] generateQrPng(String text, int width, int height)`: Render chuỗi payload sang mảng byte ảnh PNG bằng ZXing `QRCodeWriter`.

4. **`TicketService` & `TicketServiceImpl`**:
   - Cập nhật `issueTicketsForOrder(UUID orderId, UUID eventId, UUID showTimeId, String orderCode, List<OrderItemEventPayload> items)`.
   - Sinh `ticketId = UUID.randomUUID()` trước, tạo `qrPayload` ký số, gán vào Ticket và lưu vào DB.
   - Bổ sung phương thức `byte[] getTicketQrImage(UUID ticketId, int width, int height)`.

5. **`TicketIssuanceConsumer`**:
   - Truyền `payload.getEventId()` sang `ticketService.issueTicketsForOrder(...)` cho cả 2 sự kiện `payment.success` và `order.paid`.

6. **`CheckInServiceImpl`**:
   - Khi nhận `request.getQrPayload()`, gọi `ticketQrPayloadService.verifyAndParsePayload(qrPayload)`.
   - Lấy `ticketId` từ payload đã xác thực, tra cứu `ticketRepository.findByIdWithCheckIn(ticketId)`.
   - Kiểm tra vé != USED và != CANCELLED, ngăn chặn double check-in.

7. **`TicketController`**:
   - Bổ sung API `GET /api/v1/tickets/{id}/qr-code?width=300&height=300` trả về `image/png`.

## 5. Kế hoạch Kiểm thử & Xác minh
- Unit test `TicketQrPayloadServiceTest`: Sinh token, xác thực token hợp lệ, kiểm tra ngoại lệ khi token bị sửa đổi hoặc sai chữ ký bí mật.
- Unit test `QrCodeGeneratorServiceTest`: Tạo ảnh PNG từ payload, kiểm tra định dạng ảnh (kích thước, PNG header).
- Unit test `TicketServiceImplTest` & `TicketOrderIssuanceServiceTest`: Kiểm tra phát hành vé với signed QR payload, kiểm tra API lấy ảnh QR.
- Unit test `CheckInServiceImplTest`: Kiểm tra check-in qua QR token hợp lệ, từ chối token giả mạo chữ ký.
- Integration test `TicketIssuanceConsumerTest`: Kiểm tra bắt sự kiện `payment.success` và `order.paid` sinh vé đầy đủ `eventId`.
