package vn.eventplatform.promotion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"vn.eventplatform.promotion", "vn.eventplatform.common"})
@EnableFeignClients
public class PromotionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PromotionServiceApplication.class, args);
    }
}
