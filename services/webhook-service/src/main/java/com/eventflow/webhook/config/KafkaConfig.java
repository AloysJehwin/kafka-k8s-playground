package com.eventflow.webhook.config;

import com.eventflow.kafka.KafkaDefaults;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;

import java.util.HashMap;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrap;

    @Value("${eventflow.schema-registry-url}")
    private String schemaRegistry;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        var props = new HashMap<>(KafkaDefaults.consumer(bootstrap, schemaRegistry, "webhook-service"));
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "webhook-service");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> cf) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(2);
        return factory;
    }
}
