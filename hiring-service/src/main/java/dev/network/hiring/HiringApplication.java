package dev.network.hiring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"dev.network.hiring", "dev.network.web"})
@EnableScheduling
public class HiringApplication {
    public static void main(String[] args) {
        SpringApplication.run(HiringApplication.class, args);
    }
}
