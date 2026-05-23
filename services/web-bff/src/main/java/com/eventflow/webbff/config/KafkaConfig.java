package com.eventflow.webbff.config;

import com.eventflow.events.OrderCompleted;
import com.eventflow.kafka.KafkaDefaults;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrap;

    @Value("${eventflow.schema-registry-url}")
    private String schemaRegistry;

    @Bean
    public ConsumerFactory<String, OrderCompleted> orderCompletedConsumerFactory() {
        var props = new HashMap<>(KafkaDefaults.consumer(bootstrap, schemaRegistry, "web-bff-sse"));
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "web-bff");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCompleted> orderCompletedListenerFactory(
            ConsumerFactory<String, OrderCompleted> cf) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderCompleted>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(1);
        return factory;
    }
}
