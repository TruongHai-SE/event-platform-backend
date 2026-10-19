package vn.eventplatform.order.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionOperations;
import vn.eventplatform.common.exception.AppException;
import vn.eventplatform.order.client.EventServiceFallback;
import vn.eventplatform.order.client.PaymentServiceClient;
import vn.eventplatform.order.client.PromotionServiceClient;
import vn.eventplatform.order.domain.Order;
import vn.eventplatform.order.domain.OrderStatus;
import vn.eventplatform.order.dto.CreateOrderItemRequest;
import vn.eventplatform.order.dto.CreateOrderRequest;
import vn.eventplatform.order.dto.OrderResponse;
import vn.eventplatform.order.lock.InventoryLockService;
import vn.eventplatform.order.mapper.OrderItemMapper;
import vn.eventplatform.order.mapper.OrderItemMapperImpl;
import vn.eventplatform.order.mapper.OrderMapper;
import vn.eventplatform.order.mapper.OrderMapperImpl;
import vn.eventplatform.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OrderServiceConcurrencyTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PromotionServiceClient promotionServiceClient;

    @Mock
    private PaymentServiceClient paymentServiceClient;

    private EventServiceFallback eventServiceFallback;
    private OrderMapper orderMapper;
    private OrderServiceImpl orderService;
    private ThreadSafeInventoryLockService inventoryLockService;
    private final List<Order> persistedOrders = new CopyOnWriteArrayList<>();

    private final TransactionOperations transactionOperations = new TransactionOperations() {
        @Override
        public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };

    /**
     * Giả lập cơ chế khóa phân tán của Redis Redisson Lock trên môi trường đa luồng (Multi-threading).
     * Áp dụng thuật toán sắp xếp xác định sortedTicketTypeIds để chống deadlock ABBA.
     */
    static class ThreadSafeInventoryLockService implements InventoryLockService {
        private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

        @Override
        public <T> T executeWithLock(UUID showTimeId, Collection<UUID> ticketTypeIds, Supplier<T> action) {
            List<String> sortedKeys = ticketTypeIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .map(id -> "lock:" + showTimeId + ":" + id)
                    .toList();

            List<ReentrantLock> acquiredLocks = new ArrayList<>();
            for (String key : sortedKeys) {
                ReentrantLock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock(true));
                lock.lock();
                acquiredLocks.add(lock);
            }
            try {
                return action.get();
            } finally {
                // Giải phóng khóa theo thứ tự ngược lại (LIFO)
                for (int i = acquiredLocks.size() - 1; i >= 0; i--) {
                    acquiredLocks.get(i).unlock();
                }
            }
        }

        @Override
        public void executeWithLock(UUID showTimeId, Collection<UUID> ticketTypeIds, Runnable action) {
            executeWithLock(showTimeId, ticketTypeIds, () -> {
                action.run();
                return null;
            });
        }
    }

    @BeforeEach
    void setUp() {
        OrderItemMapper orderItemMapper = new OrderItemMapperImpl();
        OrderMapperImpl impl = new OrderMapperImpl();
        ReflectionTestUtils.setField(impl, "orderItemMapper", orderItemMapper);
        this.orderMapper = impl;

        this.eventServiceFallback = new EventServiceFallback();
        this.inventoryLockService = new ThreadSafeInventoryLockService();
        this.persistedOrders.clear();

        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getId() == null) {
                order.setId(UUID.randomUUID());
            }
            persistedOrders.add(order);
            return order;
        });

        this.orderService = new OrderServiceImpl(
                orderRepository,
                orderMapper,
                eventServiceFallback,
                promotionServiceClient,
                paymentServiceClient,
                inventoryLockService,
                transactionOperations,
                null
        );
        ReflectionTestUtils.setField(orderService, "internalSecret", "test-secret");
    }

    @Test
    @DisplayName("Concurrency Test: 10 luồng đồng thời tranh mua 20 vé (mỗi luồng 10 vé) -> Chỉ 2 luồng thành công, 8 luồng từ chối, không bán vượt")
    void shouldPreventOversellingUnderHighConcurrency() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        // 1. Khởi tạo kho vé: Tổng số lượng = 20 vé, giới hạn mỗi đơn = 10 vé
        int totalTickets = 20;
        int maxPerOrder = 10;
        eventServiceFallback.seedInventory(showTimeId, ticketTypeId, "Flash Sale VIP", new BigDecimal("500000"), totalTickets, maxPerOrder);

        int threadCount = 10;
        int ticketsPerOrder = 10; // Tổng nhu cầu: 10 * 10 = 100 vé (vượt xa kho 20 vé)

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<OrderResponse> successfulOrders = new CopyOnWriteArrayList<>();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UUID userId = UUID.randomUUID();
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .eventId(eventId)
                    .showTimeId(showTimeId)
                    .customerName("Customer " + i)
                    .customerEmail("customer" + i + "@example.com")
                    .items(List.of(CreateOrderItemRequest.builder()
                            .ticketTypeId(ticketTypeId)
                            .quantity(ticketsPerOrder)
                            .build()))
                    .build();

            tasks.add(() -> {
                readyLatch.countDown();
                // Chờ tất cả 10 luồng cùng tập kết sẵn sàng
                startLatch.await();

                try {
                    OrderResponse response = orderService.createOrder(userId, request);
                    if (response != null && response.getStatus() == OrderStatus.PENDING) {
                        successCount.incrementAndGet();
                        successfulOrders.add(response);
                    }
                } catch (AppException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        // Đợi tất cả 10 thread sẵn sàng ở vạch xuất phát
        readyLatch.await(5, TimeUnit.SECONDS);

        // Bắn súng xuất phát đồng loạt tại cùng 1 mili-giây
        startLatch.countDown();

        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // 2. Kiểm chứng kết quả kiểm thử đồng thời (Assertions)
        // Chỉ duy nhất 2 luồng mua thành công (2 * 10 = 20 vé)
        assertThat(successCount.get()).isEqualTo(2);

        // 8 luồng còn lại bị từ chối do hết vé khả dụng
        assertThat(failureCount.get()).isEqualTo(8);

        // Số đơn hàng được lưu trong cơ sở dữ liệu chính xác là 2
        assertThat(persistedOrders).hasSize(2);

        // Tổng số vé được đặt của tất cả các đơn thành công phải đúng bằng 20 vé (không bán vượt)
        int totalPurchased = persistedOrders.stream()
                .flatMap(o -> o.getItems().stream())
                .mapToInt(vn.eventplatform.order.domain.OrderItem::getQuantity)
                .sum();
        assertThat(totalPurchased).isEqualTo(20);
    }

    @Test
    @DisplayName("Concurrency Test: 10 luồng đồng thời mua vé số lượng nhỏ (3 vé/đơn trên kho 15 vé) -> Đúng 5 đơn thành công, 5 đơn thất bại")
    void shouldPreventOversellingWithMultipleSmallOrders() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        // Tổng kho = 15 vé
        int totalTickets = 15;
        int maxPerOrder = 5;
        eventServiceFallback.seedInventory(showTimeId, ticketTypeId, "Early Bird", new BigDecimal("200000"), totalTickets, maxPerOrder);

        int threadCount = 10;
        int ticketsPerOrder = 3; // 10 * 3 = 30 vé (kho chỉ có 15 vé -> tối đa 5 đơn)

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UUID userId = UUID.randomUUID();
            CreateOrderRequest request = CreateOrderRequest.builder()
                    .eventId(eventId)
                    .showTimeId(showTimeId)
                    .customerName("User " + i)
                    .customerEmail("user" + i + "@example.com")
                    .items(List.of(CreateOrderItemRequest.builder()
                            .ticketTypeId(ticketTypeId)
                            .quantity(ticketsPerOrder)
                            .build()))
                    .build();

            tasks.add(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    orderService.createOrder(userId, request);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Đúng 5 đơn thành công (5 * 3 = 15 vé)
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failureCount.get()).isEqualTo(5);
        assertThat(persistedOrders).hasSize(5);

        int totalOrdered = persistedOrders.stream()
                .flatMap(o -> o.getItems().stream())
                .mapToInt(vn.eventplatform.order.domain.OrderItem::getQuantity)
                .sum();
        assertThat(totalOrdered).isEqualTo(15);
    }

    @Test
    @DisplayName("Deadlock Prevention: 20 luồng đồng thời đặt nhiều loại vé ngược thứ tự nhau -> Không bao giờ bị deadlock ABBA")
    void shouldNotDeadlockWhenThreadsOrderMultipleTicketTypesInDifferentOrder() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeA = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID ticketTypeB = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // Kho dồi dào cho cả 2 loại vé
        eventServiceFallback.seedInventory(showTimeId, ticketTypeA, "Hạng A", new BigDecimal("100000"), 100, 10);
        eventServiceFallback.seedInventory(showTimeId, ticketTypeB, "Hạng B", new BigDecimal("200000"), 100, 10);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            UUID userId = UUID.randomUUID();
            // Nửa số luồng gửi danh sách [A, B], nửa số luồng gửi danh sách ngược lại [B, A]
            List<CreateOrderItemRequest> items = (i % 2 == 0)
                    ? List.of(
                            CreateOrderItemRequest.builder().ticketTypeId(ticketTypeA).quantity(1).build(),
                            CreateOrderItemRequest.builder().ticketTypeId(ticketTypeB).quantity(1).build()
                      )
                    : List.of(
                            CreateOrderItemRequest.builder().ticketTypeId(ticketTypeB).quantity(1).build(),
                            CreateOrderItemRequest.builder().ticketTypeId(ticketTypeA).quantity(1).build()
                      );

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .eventId(eventId)
                    .showTimeId(showTimeId)
                    .customerName("Deadlock Test " + i)
                    .customerEmail("deadlock" + i + "@example.com")
                    .items(items)
                    .build();

            tasks.add(() -> {
                readyLatch.countDown();
                startLatch.await();
                try {
                    orderService.createOrder(userId, request);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> f : futures) {
            // Nếu có deadlock, get() sẽ bị timeout
            f.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Tất cả 20 luồng đều hoàn thành thành công và không luồng nào bị kẹt deadlock
        assertThat(successCount.get()).isEqualTo(20);
        assertThat(persistedOrders).hasSize(20);
    }
}
