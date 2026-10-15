package vn.eventplatform.ad;
import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication; import org.springframework.cache.annotation.EnableCaching;
@SpringBootApplication(scanBasePackages={"vn.eventplatform.ad","vn.eventplatform.common"}) @EnableCaching
public class AdServiceApplication { public static void main(String[] args){SpringApplication.run(AdServiceApplication.class,args);} }
