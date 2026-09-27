package dev.network.messaging.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "network.kafka.create-topics",
    havingValue = "true")
@Configuration
public class KafkaTopics {
  @Bean
  NewTopic domainEvents() {
    return TopicBuilder.name("network.events.v1").partitions(3).replicas(1).build();
  }

  @Bean
  NewTopic deadLetters() {
    return TopicBuilder.name("network.events.v1.DLT").partitions(3).replicas(1).build();
  }
}
