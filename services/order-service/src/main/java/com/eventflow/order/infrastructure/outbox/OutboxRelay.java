package com.eventflow.order.infrastructure.outbox;

import com.eventflow.events.OrderPlaced;
import com.eventflow.events.Topics;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Polls outbox table and publishes to Kafka. After successful send, marks
 * the row processed. Producer is idempotent so duplicate publishes are safe.
 *
 * In production: replace with Debezium CDC for true exactly-once outbox relay.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OutboxRelay(OutboxRepository repository,
                       KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${eventflow.outbox.poll-interval:1000}")
    @Transactional
    public void relay() {
        var batch = repository.findUnprocessed(PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) return;

        log.debug("Relaying {} outbox events", batch.size());
        for (var event : batch) {
            try {
                var payload = deserialize(event.getPayload());
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), payload).get();
                event.markProcessed();
            } catch (Exception e) {
                log.error("Outbox relay failed for {} — will retry", event.getId(), e);
                break; // preserve ordering: stop on first failure
            }
        }
    }

    /** Decode Base64-wrapped Avro binary. Generic to keep relay payload-agnostic. */
    private Object deserialize(String base64Payload) throws Exception {
        var bytes = Base64.getDecoder().decode(base64Payload);
        DatumReader<OrderPlaced> reader = new SpecificDatumReader<>(OrderPlaced.class);
        var decoder = DecoderFactory.get().binaryDecoder(new ByteArrayInputStream(bytes), null);
        return reader.read(null, decoder);
    }
}
