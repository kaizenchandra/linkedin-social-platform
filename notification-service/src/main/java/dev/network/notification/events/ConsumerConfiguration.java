package dev.network.notification.events;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class ConsumerConfiguration {
  @Bean
  DefaultErrorHandler errors(KafkaTemplate<String, String> template, MeterRegistry metrics) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template,
            (record, error) ->
                new org.apache.kafka.common.TopicPartition(
                    "network.events.v1.DLT", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    var handler =
        new DefaultErrorHandler(
            (record, error) -> {
              recoverer.accept(record, error);
              metrics.counter("notifications.deadletters").increment();
            },
            new FixedBackOff(1000, 3));
    handler.setRetryListeners(
        (record, error, attempt) -> metrics.counter("notifications.failures").increment());
    return handler;
  }
}
