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
}
