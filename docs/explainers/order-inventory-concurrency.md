# Nghiệp Vụ & Kỹ Thuật: Quản Lý Tồn Kho & Kiểm Thử Đồng Thời (Concurrency Test) Chống Bán Vượt

> 📌 **Thông Tin Tài Liệu**  
> • **Chủ đề**: API tồn kho số vé khả dụng (Public & Internal) • Concurrency Test chống bán vượt (Overselling)  
> • **Dịch vụ liên quan**: `order-service` (8084), `event-service` (8087)  
> • **Tài liệu tham chiếu**: [order-ticket-payment.md](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/docs/explainers/order-ticket-payment.md) • [README.md](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/README.md)

---

## 1. Cơ Chế Quản Lý Tồn Kho Vé Giữa Public & event-service

### 1.1. Công Thức Tính Số Vé Khả Dụng (Available Quantity)

Trong [ShowTimeTicketInventory.java#L46-L48](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/event-service/src/main/java/vn/eventplatform/event/domain/ShowTimeTicketInventory.java#L46-L48), tồn kho vé của mỗi hạng vé trong từng suất diễn được quản lý theo 3 biến độc lập:

* 📊 **`totalQuantity`**: Tổng số vé ban đầu phát hành cho hạng vé đó.
* 🟡 **`reservedQuantity`**: Số vé đang bị tạm giữ bởi các đơn hàng đang chờ thanh toán (trong hạn 15 phút).
* 🟢 **`soldQuantity`**: Số vé đã bán thành công (đơn hàng đã chuyển sang `PAID`).
* 🏷️ **`availableQuantity` (Số vé còn lại mở bán)**:
  ```java
  public int getAvailableQuantity() {
      return Math.max(0, totalQuantity - (reservedQuantity + soldQuantity));
  }
  ```

> [!NOTE]
> Khách hàng mới chỉ có thể đặt mua tối đa bằng đúng số vé `availableQuantity` tại thời điểm đó. Khi có đơn hàng hết hạn 15 phút, `reservedQuantity` giảm xuống và vé lập tức quay lại `availableQuantity` cho người khác mua.

---

### 1.2. API Cho Public (Khách Hàng Xem Số Vé Còn Lại)

* **Điểm vào API**: `GET /api/v1/events/public/{idOrSlug}`  
* **File Controller**: [`PublicEventController.java#L58-L65`](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/event-service/src/main/java/vn/eventplatform/event/controller/PublicEventController.java#L58-L65)  
* **File Service**: [`EventService.java#L948-L1002`](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/event-service/src/main/java/vn/eventplatform/event/service/EventService.java#L948-L1002)  
* **Mô tả hoạt động**:
  - Khách hàng không cần đăng nhập vẫn có thể gọi API này để xem thông tin sự kiện theo ID hoặc Slug.
  - Hệ thống tự động gom nhóm tồn kho theo từng suất diễn (`showTimeId`) và hạng vé (`ticketTypeId`).
* **Cấu trúc dữ liệu trả về cho Client**:
  ```json
  {
    "showTimes": [
      {
        "id": "c0a80123-...",
        "startTime": "2026-11-20T19:00:00Z",
        "inventories": [
          {
            "ticketTypeId": "d1b90456-...",
            "ticketTypeName": "VIP Ticket",
            "price": 500000,
            "totalQuantity": 100,
            "reservedQuantity": 15,
            "soldQuantity": 80,
            "availableQuantity": 5
          }
        ]
      }
    ]
  }
  ```
  *(Giao diện Web/App sẽ hiển thị: "Chỉ còn 5 vé VIP!")*

---

### 1.3. Bộ API Nội Bộ Giữa `order-service` và `event-service`

Các API này nằm tại [`InternalEventController.java`](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/event-service/src/main/java/vn/eventplatform/event/controller/InternalEventController.java), được bảo vệ bằng Header bí mật `X-Internal-Secret`:

#### 1. Tạm giữ chỗ vé (`check-and-reserve`)
* **Endpoint**: `POST /internal/v1/events/inventory/check-and-reserve`
* **Xử lý tại**: [`InventoryInternalService.java#L37-L118`](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/event-service/src/main/java/vn/eventplatform/event/service/InventoryInternalService.java#L37-L118)
* **Quy trình**:
  - Kiểm tra sự kiện có đang `PUBLISHED` và suất diễn chưa kết thúc.
  - Sắp xếp thứ tự các loại vé theo mã UUID (`TreeMap`) để khóa DB theo thứ tự xác định.
  - Khóa bi quan hàng tồn kho bằng `SELECT ... FOR UPDATE` ([ShowTimeTicketInventoryRepository.java#L50-L60](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/event-service/src/main/java/vn/eventplatform/event/repository/ShowTimeTicketInventoryRepository.java#L50-L60)).
  - Chạy câu lệnh SQL nguyên tử tăng `reservedQuantity`:
    ```sql
    UPDATE show_time_ticket_inventories 
    SET reserved_quantity = reserved_quantity + :qty 
    WHERE id = :id AND (total_quantity - (reserved_quantity + sold_quantity)) >= :qty;
    ```
  - Trả về snapshot đơn giá niêm yết chính thức từ DB cho `order-service`.

#### 2. Giải phóng vé giữ chỗ (`release`)
* **Endpoint**: `POST /internal/v1/events/inventory/release`
* **Xử lý tại**: [`InventoryInternalService.java#L120-L151`](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/event-service/src/main/java/vn/eventplatform/event/service/InventoryInternalService.java#L120-L151)
* **Kích hoạt khi**: Khách hủy đơn PENDING hoặc đơn quá hạn 15 phút chưa trả tiền.
* **Tác động**: Trừ `reservedQuantity` tương ứng, giúp vé quay lại trạng thái mở bán ngay lập tức.

#### 3. Chốt vé đã bán (`confirm`)
* **Endpoint**: `POST /internal/v1/events/inventory/confirm`
* **Xử lý tại**: [`InventoryInternalService.java#L153-L184`](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/event-service/src/main/java/vn/eventplatform/event/service/InventoryInternalService.java#L153-L184)
* **Kích hoạt khi**: Đơn hàng chuyển sang `PAID`.
* **Tác động**: Trừ `reservedQuantity` và tăng `soldQuantity` tương ứng.

---

## 2. Kiểm Thử Đồng Thời (Concurrency Test) Chống Bán Vượt (Oversell)

Để đảm bảo hệ thống không bao giờ bán vượt dù có hàng ngàn request ập vào mua cùng một giây, dự án xây dựng 2 tầng kiểm thử đồng thời:

### 2.1. Tầng 1: Unit & Concurrency Test Của Khóa Phân Tán (Redis Redisson)

* **File kiểm thử**: [`RedissonInventoryLockServiceTest.java`](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/order-service/src/test/java/vn/eventplatform/order/lock/RedissonInventoryLockServiceTest.java)  
* **Mục tiêu**: Kiểm chứng logic xin khóa phân tán và xử lý tranh chấp ở `order-service`.

#### 🧪 Test Case 1: Chống Deadlock ABBA Khi Đặt Nhiều Hạng Vé
* **Phương thức**: `shouldAcquireAndReleaseMultiLockWithDeterministicSortedKeys()` ([Dòng 74-101](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/order-service/src/test/java/vn/eventplatform/order/lock/RedissonInventoryLockServiceTest.java#L74-L101))
* **Kịch bản**: Client gửi danh sách 2 hạng vé theo thứ tự ngẫu nhiên `[UUID_2, UUID_1]`.
* **Kết quả**: `RedissonInventoryLockService` tự động sắp xếp lại thành `[UUID_1, UUID_2]` trước khi xin khóa `RedissonMultiLock`. Nhờ đó, tất cả các thread đồng thời đều xin lock theo thứ tự từ bé đến lớn, loại trừ hoàn toàn nguy cơ Deadlock khóa chéo.

#### 🧪 Test Case 2: Xử Lý Khi Tranh Chấp Quá Cao
* **Phương thức**: `shouldThrowBadRequestWhenLockCannotBeAcquired()` ([Dòng 103-126](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/order-service/src/test/java/vn/eventplatform/order/lock/RedissonInventoryLockServiceTest.java#L103-L126))
* **Kịch bản**: Khóa đang bị luồng khác chiếm giữ quá thời gian chờ (5 giây).
* **Kết quả**: Hệ thống không để thread bị treo vô hạn mà lập tức ném ra lỗi `AppException` với thông báo: *"Hệ thống đang có nhiều lượt giữ chỗ cùng lúc cho hạng vé này. Vui lòng thử lại sau giây lát."*

#### 🧪 Test Case 3: Đảm Bảo Giải Phóng Khóa Trong Khối Finally
* **Phương thức**: `shouldAlwaysReleaseLockInFinallyBlockEvenIfActionThrows()` ([Dòng 128-150](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/order-service/src/test/java/vn/eventplatform/order/lock/RedissonInventoryLockServiceTest.java#L128-L150))
* **Kịch bản**: Tác vụ gọi DB bên trong xảy ra lỗi ngoại lệ (Exception).
* **Kết quả**: Khối `finally` bảo đảm luôn gọi `lock.unlock()`, không để lại "khóa chết" (Zombie Lock) trên Redis làm kẹt vé của người khác.

---

### 2.2. Tầng 2: Integration Concurrency Test Mô Phỏng Đua Lệnh (Race Condition)

* **File kiểm thử**: [`EventFlowIntegrationTest.java#L713-L770`](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/event-service/src/test/java/vn/eventplatform/event/EventFlowIntegrationTest.java#L713-L770)  
* **Mục tiêu**: Giả lập tình huống thực tế khi mở bán vé Flash Sale: **Tổng số vé trong kho ít hơn rất nhiều so với tổng số lượng các luồng đồng thời tranh mua**.

#### 🎯 Thiết Lập Kịch Bản Đua Lệnh
* Khởi tạo một hạng vé mở bán với **tổng số lượng trong kho = 20 vé**.
* Giới hạn mỗi đơn được mua tối đa: `maxPerOrder = 10 vé`.
* Tạo **10 Luồng (Threads)** chạy song song thông qua `ExecutorService` và `CountDownLatch`:
  ```java
  int threadCount = 10;
  ExecutorService pool = Executors.newFixedThreadPool(threadCount);
  CountDownLatch readyLatch = new CountDownLatch(threadCount);
  CountDownLatch startLatch = new CountDownLatch(1);
  ```
* Mỗi luồng cùng gửi request đặt mua **10 vé** tại cùng một thời điểm:
  - **Tổng số vé các luồng đòi mua**: $10 \text{ luồng} \times 10 \text{ vé} = 100 \text{ vé}$.
  - **Số vé thực tế có trong kho**: chỉ có **20 vé**.
  - Tất cả 10 luồng tập kết qua `readyLatch.await()`, sau đó đồng loạt xuất phát cùng 1 mili-giây khi `startLatch.countDown()`.

#### 📊 Kết Quả Kiểm Chứng (Assertions)
Kiểm tra phản hồi thực tế từ MockMvc:
```java
// Chỉ duy nhất 2 luồng mua thành công (2 x 10 = 20 vé, vừa hết kho)
assertThat(successCount.get()).isEqualTo(2);

// 8 luồng còn lại nhận lỗi HTTP 409 CONFLICT (Hết vé)
assertThat(conflictCount.get()).isEqualTo(8);

// Kiểm tra lại trạng thái trong Cơ sở dữ liệu:
var inv = inventoryRepository.findByShowTimeId(showTimeId).get(0);

// Số vé giữ chỗ đạt mức tối đa bằng đúng kho:
assertThat(inv.getReservedQuantity()).isEqualTo(20);

// Số vé khả dụng còn lại chạm đáy 0, TUYỆT ĐỐI KHÔNG BỊ ÂM (Không bán vượt):
assertThat(inv.getAvailableQuantity()).isEqualTo(0);
```

---

## 3. Tổng Hợp Cơ Chế Phòng Thủ Đa Tầng Chống Bán Vượt

```
[ Khách Hàng Gửi Request Đặt Vé ]
                │
                ▼
[ TẦNG 1: order-service (Redisson Distributed Lock) ]
  • Khóa phân tán Redis theo showTimeId + ticketTypeIds
  • Sắp xếp danh sách ticketTypeId theo Alphabet chống Deadlock ABBA
  • Chặn đứng các request trùng lặp từ tầng ứng dụng
                │
                ▼
[ TẦNG 2: event-service (Khóa Bi Quan Database) ]
  • SELECT ... FOR UPDATE trên hàng tồn kho show_time_ticket_inventories
  • Sắp xếp ID vé trong câu truy vấn để khóa DB tuần tự
                │
                ▼
[ TẦNG 3: Atomic SQL Update Tại Engine DB ]
  • UPDATE ... SET reserved = reserved + qty WHERE (total - reserved - sold) >= qty
  • Trả về số dòng cập nhật. Nếu = 0 -> Ném lỗi CONFLICT (Hết vé)
                │
                ▼
[ KẾT QUẢ: 100% Không Bao Giờ Xảy Ra Bán Vượt ]
```
