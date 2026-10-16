package vn.eventplatform.order.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;
import vn.eventplatform.common.dto.ErrorCode;
import vn.eventplatform.common.exception.AppException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedissonInventoryLockServiceTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock singleLock;

    @Mock
    private RLock multiLock;

    private RedissonInventoryLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new RedissonInventoryLockService(redissonClient);
        ReflectionTestUtils.setField(lockService, "waitTimeSeconds", 5L);
        ReflectionTestUtils.setField(lockService, "leaseTimeSeconds", -1L);
    }

    @Test
    @DisplayName("Lock đơn: Lấy và giải phóng lock thành công cho 1 hạng vé")
    void shouldAcquireAndReleaseSingleLockSuccessfully() throws InterruptedException {
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        String expectedKey = "lock:inventory:showtime:" + showTimeId + ":ticket-type:" + ticketTypeId;

        when(redissonClient.getLock(expectedKey)).thenReturn(singleLock);
        when(singleLock.tryLock(5L, TimeUnit.SECONDS)).thenReturn(true);
        when(singleLock.isHeldByCurrentThread()).thenReturn(true);

        AtomicBoolean executed = new AtomicBoolean(false);
        String result = lockService.executeWithLock(showTimeId, List.of(ticketTypeId), () -> {
            executed.set(true);
            return "SUCCESS";
        });

        assertThat(result).isEqualTo("SUCCESS");
        assertThat(executed.get()).isTrue();

        verify(redissonClient).getLock(expectedKey);
        verify(singleLock).tryLock(5L, TimeUnit.SECONDS);
        verify(singleLock).unlock();
    }

    @Test
    @DisplayName("MultiLock: Sắp xếp các hạng vé theo thứ tự xác định để chống deadlock khi có nhiều hạng vé")
    void shouldAcquireAndReleaseMultiLockWithDeterministicSortedKeys() throws InterruptedException {
        UUID showTimeId = UUID.randomUUID();
        UUID uuid1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID uuid2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // Truyền vào danh sách ngược thứ tự: [uuid2, uuid1]
        List<UUID> unsortedTicketTypes = List.of(uuid2, uuid1);

        String key1 = "lock:inventory:showtime:" + showTimeId + ":ticket-type:" + uuid1;
        String key2 = "lock:inventory:showtime:" + showTimeId + ":ticket-type:" + uuid2;

        RLock lock1 = mock(RLock.class);
        RLock lock2 = mock(RLock.class);

        when(redissonClient.getLock(key1)).thenReturn(lock1);
        when(redissonClient.getLock(key2)).thenReturn(lock2);
        when(redissonClient.getMultiLock(lock1, lock2)).thenReturn(multiLock);
        when(multiLock.tryLock(5L, TimeUnit.SECONDS)).thenReturn(true);
        when(multiLock.isHeldByCurrentThread()).thenReturn(true);

        String result = lockService.executeWithLock(showTimeId, unsortedTicketTypes, () -> "ORDER_CREATED");

        assertThat(result).isEqualTo("ORDER_CREATED");
        verify(redissonClient).getMultiLock(lock1, lock2);
        verify(multiLock).tryLock(5L, TimeUnit.SECONDS);
        verify(multiLock).unlock();
    }

    @Test
    @DisplayName("Chống oversell: Ném AppException BAD_REQUEST khi không thể lấy lock do tranh chấp cao")
    void shouldThrowBadRequestWhenLockCannotBeAcquired() throws InterruptedException {
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        String key = "lock:inventory:showtime:" + showTimeId + ":ticket-type:" + ticketTypeId;

        when(redissonClient.getLock(key)).thenReturn(singleLock);
        when(singleLock.tryLock(5L, TimeUnit.SECONDS)).thenReturn(false);

        AtomicBoolean executed = new AtomicBoolean(false);

        assertThatThrownBy(() ->
                lockService.executeWithLock(showTimeId, List.of(ticketTypeId), () -> {
                    executed.set(true);
                    return "DONE";
                })
        )
                .isInstanceOf(AppException.class)
                .hasMessageContaining("nhiều lượt giữ chỗ cùng lúc");

        assertThat(executed.get()).isFalse();
        verify(singleLock, never()).unlock();
    }

    @Test
    @DisplayName("Bảo đảm giải phóng lock trong khối finally dù tác vụ bên trong ném Exception")
    void shouldAlwaysReleaseLockInFinallyBlockEvenIfActionThrows() throws InterruptedException {
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        String key = "lock:inventory:showtime:" + showTimeId + ":ticket-type:" + ticketTypeId;

        when(redissonClient.getLock(key)).thenReturn(singleLock);
        when(singleLock.tryLock(5L, TimeUnit.SECONDS)).thenReturn(true);
        when(singleLock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() ->
                lockService.executeWithLock(showTimeId, List.of(ticketTypeId), () -> {
                    throw new IllegalStateException("Database error during reservation");
                })
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database error during reservation");

        // Lock vẫn phải được unlock dù có lỗi
        verify(singleLock).unlock();
    }

    @Test
    @DisplayName("Xác thực đầu vào: Ném BAD_REQUEST khi showTimeId null hoặc danh sách hạng vé rỗng")
    void shouldValidateNullAndEmptyInputs() {
        UUID ticketTypeId = UUID.randomUUID();

        // showTimeId null
        assertThatThrownBy(() -> lockService.executeWithLock(null, List.of(ticketTypeId), () -> "FAIL"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("ID suất diễn không được để trống");

        // ticketTypeIds rỗng
        assertThatThrownBy(() -> lockService.executeWithLock(UUID.randomUUID(), Collections.emptyList(), () -> "FAIL"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Danh sách hạng vé không được để trống");
    }

    @Test
    @DisplayName("Xử lý Runnable overload hoạt động chuẩn xác")
    void shouldExecuteRunnableOverload() throws InterruptedException {
        UUID showTimeId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        String key = "lock:inventory:showtime:" + showTimeId + ":ticket-type:" + ticketTypeId;

        when(redissonClient.getLock(key)).thenReturn(singleLock);
        when(singleLock.tryLock(5L, TimeUnit.SECONDS)).thenReturn(true);
        when(singleLock.isHeldByCurrentThread()).thenReturn(true);

        AtomicBoolean executed = new AtomicBoolean(false);
        lockService.executeWithLock(showTimeId, List.of(ticketTypeId), () -> executed.set(true));

        assertThat(executed.get()).isTrue();
        verify(singleLock).unlock();
    }
}
