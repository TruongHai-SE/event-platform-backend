# Ticket QR Payload & Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai sinh chuỗi QR payload ký số HMAC-SHA256 (chứa ticketId, eventId, showTimeId, ticketTypeId, ticketTypeName, orderId) và tích hợp ZXing sinh ảnh mã QR cho vé điện tử trong `ticket-service`.

**Architecture:** Sử dụng JJWT `0.12.6` để ký số JWT payload với HMAC-SHA256; sử dụng ZXing `3.5.3` để render mã QR dạng ảnh PNG; cập nhật `TicketIssuanceConsumer`, `TicketServiceImpl`, `CheckInServiceImpl` và `TicketController`.

**Tech Stack:** Java 21, Spring Boot 3.3.4, JJWT 0.12.6, ZXing 3.5.3, JUnit 5, Mockito, AssertJ.

## Global Constraints
- Java 21 LTS (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)`)
- Maven compile and test commands run from workspace root or `-pl ticket-service`
- Chữ ký bí mật lấy từ `security.ticket.qr-secret` hoặc fallback sang `security.token.secret-key`

---

### Task 1: Add ZXing Dependencies and QR Secret Configuration

**Files:**
- Modify: `ticket-service/pom.xml`
- Modify: `ticket-service/src/main/resources/application.yml`
- Modify: `ticket-service/src/test/resources/application-test.yml`

- [ ] **Step 1: Add ZXing dependencies to `ticket-service/pom.xml`**
- [ ] **Step 2: Add `security.ticket.qr-secret` in `application.yml` and `application-test.yml`**
- [ ] **Step 3: Verify build compiles cleanly**
Run: `export JAVA_HOME=$(/usr/libexec/java_home -v 21) && mvn compile -pl ticket-service`
- [ ] **Step 4: Commit**

---

### Task 2: Implement `TicketQrPayloadDto` and `TicketQrPayloadService`

**Files:**
- Create: `ticket-service/src/main/java/vn/eventplatform/ticket/dto/TicketQrPayloadDto.java`
- Create: `ticket-service/src/main/java/vn/eventplatform/ticket/service/TicketQrPayloadService.java`
- Create: `ticket-service/src/main/java/vn/eventplatform/ticket/service/TicketQrPayloadServiceImpl.java`
- Create: `ticket-service/src/test/java/vn/eventplatform/ticket/service/TicketQrPayloadServiceTest.java`

- [ ] **Step 1: Write unit tests for payload generation, validation, tampering detection**
- [ ] **Step 2: Run test to verify failure**
- [ ] **Step 3: Implement `TicketQrPayloadDto` and `TicketQrPayloadServiceImpl`**
- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**

---

### Task 3: Implement `QrCodeGeneratorService` using ZXing

**Files:**
- Create: `ticket-service/src/main/java/vn/eventplatform/ticket/service/QrCodeGeneratorService.java`
- Create: `ticket-service/src/main/java/vn/eventplatform/ticket/service/QrCodeGeneratorServiceImpl.java`
- Create: `ticket-service/src/test/java/vn/eventplatform/ticket/service/QrCodeGeneratorServiceTest.java`

- [ ] **Step 1: Write unit test for generating PNG QR image bytes and Base64**
- [ ] **Step 2: Run test to verify failure**
- [ ] **Step 3: Implement `QrCodeGeneratorServiceImpl` with `QRCodeWriter` and `MatrixToImageWriter`**
- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**

---

### Task 4: Update `TicketService` with Signed QR Generation and Image Retrieval

**Files:**
- Modify: `ticket-service/src/main/java/vn/eventplatform/ticket/service/TicketService.java`
- Modify: `ticket-service/src/main/java/vn/eventplatform/ticket/service/TicketServiceImpl.java`
- Modify: `ticket-service/src/test/java/vn/eventplatform/ticket/service/TicketOrderIssuanceServiceTest.java`

- [ ] **Step 1: Update interface and write tests for `issueTicketsForOrder` with `eventId` and `getTicketQrImage`**
- [ ] **Step 2: Implement logic in `TicketServiceImpl`**
- [ ] **Step 3: Run tests to verify they pass**
- [ ] **Step 4: Commit**

---

### Task 5: Update `TicketIssuanceConsumer` to Pass `eventId`

**Files:**
- Modify: `ticket-service/src/main/java/vn/eventplatform/ticket/messaging/TicketIssuanceConsumer.java`
- Modify: `ticket-service/src/test/java/vn/eventplatform/ticket/messaging/TicketIssuanceConsumerTest.java`

- [ ] **Step 1: Update `TicketIssuanceConsumer` to pass `payload.getEventId()` to `ticketService.issueTicketsForOrder(...)`**
- [ ] **Step 2: Update `TicketIssuanceConsumerTest`**
- [ ] **Step 3: Run tests to verify they pass**
- [ ] **Step 4: Commit**

---

### Task 6: Update `CheckInServiceImpl` to Verify HMAC Signature Before Checking Ticket

**Files:**
- Modify: `ticket-service/src/main/java/vn/eventplatform/ticket/service/CheckInServiceImpl.java`
- Modify: `ticket-service/src/test/java/vn/eventplatform/ticket/service/TicketCancellationAndCheckInTest.java`

- [ ] **Step 1: Update `CheckInServiceImpl` to verify token using `ticketQrPayloadService` and resolve `ticketId`**
- [ ] **Step 2: Add tests for invalid/tampered QR rejection in `TicketCancellationAndCheckInTest`**
- [ ] **Step 3: Run tests to verify they pass**
- [ ] **Step 4: Commit**

---

### Task 7: Add `GET /api/v1/tickets/{id}/qr-code` to `TicketController`

**Files:**
- Modify: `ticket-service/src/main/java/vn/eventplatform/ticket/controller/TicketController.java`
- Create: `ticket-service/src/test/java/vn/eventplatform/ticket/controller/TicketControllerTest.java`

- [ ] **Step 1: Add endpoint `GET /api/v1/tickets/{id}/qr-code` returning `MediaType.IMAGE_PNG_VALUE`**
- [ ] **Step 2: Write test for `TicketControllerTest`**
- [ ] **Step 3: Run tests to verify they pass**
- [ ] **Step 4: Commit**

---

### Task 8: Full Verification, Push Branch, Merge to dev and main

**Files:**
- All modified modules

- [ ] **Step 1: Run full test suite with Maven (`mvn test -pl ticket-service`)**
- [ ] **Step 2: Push commit on branch `HaoHV2k5/feat-ticket-qr`**
- [ ] **Step 3: Merge into `dev` branch**
- [ ] **Step 4: Merge `dev` into `main` branch**
- [ ] **Step 5: Verify git log and working tree clean**
