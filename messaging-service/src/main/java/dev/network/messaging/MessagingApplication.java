package dev.network.messaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"dev.network.messaging", "dev.network.web"})
@EnableScheduling
public class MessagingApplication {
  public static void main(String[] args) {
    SpringApplication.run(MessagingApplication.class, args);
  }
}
