package dev.network.notification.events;

import org.apache.kafka.clients.producer.*;
import org.springframework.context.annotation.*;
import org.springframework.kafka.support.ProducerListener;

@Configuration
public class KafkaLogging {
    @Bean
    ProducerListener<Object, Object> producerListener() {
        return new ProducerListener<>() {
            @Override
            public void onError(
                    ProducerRecord<Object, Object> record, RecordMetadata metadata, Exception exception) {
                org.slf4j.LoggerFactory.getLogger(KafkaLogging.class)
                        .warn("Kafka publication failed type={}", exception.getClass().getSimpleName());
            }
        };
    }
}
