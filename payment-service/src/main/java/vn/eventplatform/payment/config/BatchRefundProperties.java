package vn.eventplatform.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "payment.refund.batch")
public class BatchRefundProperties {

    /**
     * Kích thước mỗi chunk xử lý hoàn tiền (mặc định 50 yêu cầu/chunk).
     */
    private int defaultChunkSize = 50;

    /**
     * Giới hạn tần suất xử lý hoàn tiền (số request tối đa mỗi giây sang cổng thanh toán / gateway).
     */
    private double defaultRequestsPerSecond = 10.0;

    /**
     * Khoảng thời gian nghỉ giữa các chunk (milliseconds) để giảm tải database và gateway.
     */
    private long delayBetweenChunksMs = 50L;

    /**
     * Số lần retry tối đa cho mỗi yêu cầu hoàn tiền khi gặp lỗi tạm thời (mạng, timeout, 5xx). Mặc định: 3 lần.
     */
    private int maxRetries = 3;

    /**
     * Thời gian chờ ban đầu trước khi retry lần đầu (milliseconds). Mặc định: 100ms.
     */
    private long initialBackoffMs = 100L;

    /**
     * Hệ số nhân tăng thời gian chờ sau mỗi lần retry thất bại (exponential backoff). Mặc định: 2.0.
     */
    private double backoffMultiplier = 2.0;

    /**
     * Thời gian timeout tối đa cho từng thao tác hoàn tiền (milliseconds). Mặc định: 5000ms (5 giây).
     */
    private long itemTimeoutMs = 5000L;
}
