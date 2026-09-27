package dev.network.member;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"dev.network.member", "dev.network.web"})
@EnableScheduling
public class MemberApplication {
  public static void main(String[] args) {
    SpringApplication.run(MemberApplication.class, args);
  }
}
