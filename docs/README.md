# Thư Viện Tài Liệu Dự Án (Documentation & Explainers)

Thư mục này lưu trữ các tài liệu tổng hợp nghiệp vụ, kiến trúc kỹ thuật và giải thích chi tiết về các phân hệ trong hệ thống **Event & Festival Management Platform (Tikzy)**.

Mỗi khi bạn yêu cầu giải thích hoặc tổng hợp thông qua `/codebase-explainer`, một tài liệu chi tiết sẽ được tự động cập nhật hoặc tạo mới tại thư mục `docs/explainers/`.

---

## Danh Sách Tài Liệu Đã Tổng Hợp

| STT | Tài liệu | Phân hệ liên quan | Nội dung chính |
| :---: | :--- | :--- | :--- |
| 1 | [Order — Ticket — Payment Explainer](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/docs/explainers/order-ticket-payment.md) | `order-service`, `ticket-service`, `payment-service` | Nghiệp vụ giữ chỗ 15 phút, snapshot giá vé, tích hợp VNPay & SePay VietQR, cơ chế retry đổi cổng, chống overselling với Redis Redisson Lock, deadlock prevention, chống double check-in và RabbitMQ event-driven. |
| 2 | [Order Inventory & Concurrency Test Explainer](file:///Users/ttcenter/workplace/mss301/code/event-platform-backend/docs/explainers/order-inventory-concurrency.md) | `order-service`, `event-service` | Cơ chế tính số vé khả dụng cho public & internal IPC, chi tiết bài kiểm thử đồng thời (10 luồng tranh mua 20 vé) chứng minh 100% không bị bán vượt (oversell). |

---

## Cấu Trúc Thư Mục `docs/`

```text
docs/
├── README.md                           # Mục lục danh mục tài liệu tổng hợp
└── explainers/                         # Các tài liệu tổng hợp chi tiết theo nghiệp vụ
    ├── order-ticket-payment.md         # Phân hệ Order, Ticket và Payment
    └── order-inventory-concurrency.md  # API Tồn kho & Concurrency Test chống Oversell
```
