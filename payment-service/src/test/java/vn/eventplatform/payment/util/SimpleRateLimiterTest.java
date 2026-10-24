package vn.eventplatform.payment.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleRateLimiterTest {

    @Test
    @DisplayName("Khởi tạo SimpleRateLimiter với rate hợp lệ")
    void constructor_ValidRate() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(5.0);
        assertThat(limiter.getPermitsPerSecond()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Khởi tạo SimpleRateLimiter với rate <= 0 sẽ dùng giá trị mặc định")
    void constructor_InvalidRate_UsesDefault() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(-1.0);
        assertThat(limiter.getPermitsPerSecond()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("acquire() hoàn thành và giới hạn tốc độ một cách mượt mà")
    void acquire_SmoothRateLimiting() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(50.0); // 50 rps => 20ms/request
        long start = System.currentTimeMillis();
        for (int i = 0; i < 3; i++) {
            limiter.acquire();
        }
        long duration = System.currentTimeMillis() - start;
        // 3 requests tại 50 rps: request 0 (0ms), request 1 (+20ms), request 2 (+20ms) => ~40ms
        assertThat(duration).isGreaterThanOrEqualTo(30L);
    }
}
