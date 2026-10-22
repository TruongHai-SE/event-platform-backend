# Design Spec: Payment Expiration Reconciliation Scheduler

- **Feature**: Payment Expiration Reconciliation Scheduler (Tự động quét và hủy giao dịch PENDING quá 15 phút)
- **Module**: `payment-service`
- **Date**: 2026-10-22
- **Status**: Approved

---

## 1. Context & Business Need

Hiện tại trong hệ thống Tikzy:
- Khi khách hàng tiến hành thanh toán, một bản ghi `Payment` được tạo với trạng thái `PENDING` trong database `event_payment_db`.
- Phân hệ `order-service` có sẵn `OrderExpirationScheduler` quét mỗi 30 giây để tự động chuyển đơn hàng `PENDING` quá 15 phút sang `EXPIRED`, đồng thời trả tồn kho vé và hoàn voucher.
- Tuy nhiên, trong `payment-service`, các bản ghi `Payment` cũ chưa hoàn tất thanh toán vẫn bị lưu ở trạng thái `PENDING` vô thời hạn. Điều này khiến dữ liệu thanh toán bị treo, không đồng bộ với vòng đời của đơn hàng.

**Mục tiêu:**
Xây dựng một Scheduled Task chạy nền trong `payment-service` tự động quét các giao dịch `Payment` ở trạng thái `PENDING` đã tồn tại quá 15 phút tính từ thời điểm tạo (`createdAt`) và chuyển trạng thái sang `CANCELLED`.

---

## 2. Technical Architecture & Constraints

### 2.1. Phân tầng & Kiến trúc
- **Tự chủ (Decoupled & Database-per-Service)**: `payment-service` tự động xử lý vòng đời của bản ghi thanh toán mà không phụ thuộc vào network call sang `order-service` hay cổng bên thứ ba (VNPay Sandbox).
- **Nguyên tử & Idempotent (Concurrency Safe)**: Sử dụng Conditional Update ở tầng Database (`updatePaymentStatusIfMatch`) để đảm bảo dù có nhiều instance của `payment-service` cùng chạy hoặc có callback từ cổng thanh toán tới đúng thời điểm quét, không bao giờ xảy ra race condition hay cập nhật đè lên trạng thái `SUCCESS`.

### 2.2. Luồng xử lý chi tiết (Flow)
1. `PaymentExpirationScheduler` chạy định kỳ mỗi 30 giây (có thể cấu hình qua file `application.yml`).
2. Tính mốc thời gian quá hạn: `threshold = Instant.now().minus(thresholdMinutes, ChronoUnit.MINUTES)`.
3. Query database tìm tất cả bản ghi `Payment` có `status = PENDING` và `createdAt <= threshold`.
4. Với mỗi bản ghi tìm thấy:
   - Thực thi câu lệnh SQL có điều kiện:
     ```sql
     UPDATE payments 
     SET status = 'CANCELLED', updated_at = NOW() 
     WHERE id = :id AND status = 'PENDING';
     ```
   - Nếu `rows > 0`: Cập nhật thành công $\rightarrow$ Ghi log INFO, bắn sự kiện `payment.failed` (lý do: "Giao dịch thanh toán quá hạn 15 phút").
   - Nếu `rows == 0`: Bỏ qua (giao dịch đã được cập nhật thành `SUCCESS` hoặc `CANCELLED` bởi luồng khác).

---

## 3. Data & Interface Changes

### 3.1. Repository (`PaymentRepository`)
Bổ sung query:
```java
List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, Instant threshold);
```
Tái sử dụng:
```java
int updatePaymentStatusIfMatch(UUID id, PaymentStatus newStatus, PaymentStatus expectedStatus, String transactionId, Instant paidAt, Instant now);
```

### 3.2. Service Interface (`PaymentService`)
Bổ sung phương thức:
```java
int cancelExpiredPendingPayments();
```

### 3.3. Service Implementation (`PaymentServiceImpl`)
- Triển khai logic tính `threshold` (mặc định 15 phút, đọc từ cấu hình).
- Lặp qua danh sách payment hết hạn, gọi `updatePaymentStatusIfMatch(id, CANCELLED, PENDING, null, null, Instant.now())`.
- Nếu cập nhật thành công, gọi `publishPaymentFailedEvent(payment, "Giao dịch thanh toán quá hạn 15 phút")`.
- Trả về số lượng bản ghi đã hủy.

### 3.4. Scheduler Component (`PaymentExpirationScheduler`)
Tạo class: `vn.eventplatform.payment.scheduler.PaymentExpirationScheduler`
- `@Component`
- `@ConditionalOnProperty(name = "payment.scheduler.enabled", havingValue = "true", matchIfMissing = true)`
- `@Scheduled(fixedDelayString = "${payment.scheduler.expiration-check-interval-ms:30000}", initialDelayString = "${payment.scheduler.expiration-initial-delay-ms:10000}")`
- Gọi `paymentService.cancelExpiredPendingPayments()`.

### 3.5. Application Configuration
- Bật `@EnableScheduling` trong `PaymentServiceApplication.java`.
- Cấu hình trong `payment-service/src/main/resources/application.yml`:
  ```yaml
  payment:
    scheduler:
      enabled: ${PAYMENT_SCHEDULER_ENABLED:true}
      expiration-check-interval-ms: ${PAYMENT_EXPIRATION_CHECK_INTERVAL_MS:30000}
      expiration-initial-delay-ms: ${PAYMENT_EXPIRATION_INITIAL_DELAY_MS:10000}
      expiration-threshold-minutes: ${PAYMENT_EXPIRATION_THRESHOLD_MINUTES:15}
  ```

---

## 4. Test Strategy

1. **Repository Test / Service Test (`PaymentServiceTest`)**:
   - `cancelExpiredPendingPayments_success`: Payment PENDING quá 15 phút được chuyển sang `CANCELLED`.
   - `cancelExpiredPendingPayments_ignoreRecent`: Payment PENDING dưới 15 phút không bị ảnh hưởng.
   - `cancelExpiredPendingPayments_idempotent`: Payment bị luồng khác đổi trạng thái (rows = 0) thì không bắn event hủy.
2. **Scheduler Test (`PaymentExpirationSchedulerTest`)**:
   - Kiểm tra `scanAndCancelExpiredPayments` gọi đúng service và ghi nhận số lượng bản ghi.
