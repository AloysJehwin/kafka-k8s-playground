package com.eventflow.order.config;

import com.eventflow.kafka.KafkaDefaults;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrap;

    @Value("${eventflow.schema-registry-url}")
    private String schemaRegistry;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        var props = new HashMap<>(KafkaDefaults.producer(bootstrap, schemaRegistry));
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "order-service");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }
}
