package dev.network.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"dev.network.content", "dev.network.web"})
@EnableScheduling
public class ContentApplication {
  public static void main(String[] args) {
    SpringApplication.run(ContentApplication.class, args);
  }
}
