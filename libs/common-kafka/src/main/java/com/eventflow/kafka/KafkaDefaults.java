package com.eventflow.kafka;

import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Map;

/**
 * Production-grade Kafka client defaults. Services pull these and override
 * only when needed (e.g. transactional.id for the producer).
 */
public final class KafkaDefaults {

    private KafkaDefaults() {}

    public static Map<String, Object> producer(String bootstrap, String schemaRegistry) {
        return Map.ofEntries(
            Map.entry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap),
            Map.entry(ProducerConfig.ACKS_CONFIG, "all"),
            Map.entry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true),
            Map.entry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5),
            Map.entry(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE),
            Map.entry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000),
            Map.entry(ProducerConfig.LINGER_MS_CONFIG, 20),
            Map.entry(ProducerConfig.BATCH_SIZE_CONFIG, 32_768),
            Map.entry(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4"),
            Map.entry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer"),
            Map.entry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                "io.confluent.kafka.serializers.KafkaAvroSerializer"),
            Map.entry(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistry),
            Map.entry(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, true)
        );
    }

    public static Map<String, Object> consumer(String bootstrap, String schemaRegistry, String groupId) {
        return Map.ofEntries(
            Map.entry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap),
            Map.entry(ConsumerConfig.GROUP_ID_CONFIG, groupId),
            Map.entry(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
            Map.entry(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false),
            Map.entry(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed"),
            Map.entry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500),
            Map.entry(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30_000),
            Map.entry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringDeserializer"),
            Map.entry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "io.confluent.kafka.serializers.KafkaAvroDeserializer"),
            Map.entry(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistry),
            Map.entry(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true)
        );
    }
}
