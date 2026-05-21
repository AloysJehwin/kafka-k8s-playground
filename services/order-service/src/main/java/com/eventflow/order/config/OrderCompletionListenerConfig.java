package com.eventflow.order.config;

import com.eventflow.events.OrderCompleted;
import com.eventflow.events.OrderStatus;
import com.eventflow.events.Topics;
import com.eventflow.kafka.ErrorHandlers;
import com.eventflow.kafka.KafkaDefaults;
import com.eventflow.order.infrastructure.persistence.OrderRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.UUID;

@Configuration
@EnableKafka
public class OrderCompletionListenerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrap;

    @Value("${eventflow.schema-registry-url}")
    private String schemaRegistry;

    @Bean
    public ConsumerFactory<String, OrderCompleted> orderCompletedConsumerFactory() {
        var props = new HashMap<>(KafkaDefaults.consumer(bootstrap, schemaRegistry, "order-service"));
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "order-service-completion");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCompleted> orderCompletedListenerFactory(
            ConsumerFactory<String, OrderCompleted> cf,
            KafkaTemplate<String, Object> kafkaTemplate) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderCompleted>();
        factory.setConsumerFactory(cf);
        factory.setCommonErrorHandler(ErrorHandlers.retryingHandler(kafkaTemplate));
        factory.setConcurrency(3);
        return factory;
    }

    @Component
    static class Listener {
        private static final Logger log = LoggerFactory.getLogger(Listener.class);
        private final OrderRepository orders;

        Listener(OrderRepository orders) {
            this.orders = orders;
        }

        @KafkaListener(
            topics = Topics.ORDERS_COMPLETED,
            containerFactory = "orderCompletedListenerFactory",
            groupId = "order-service-completion"
        )
        @Transactional
        public void onCompleted(OrderCompleted event) {
            log.info("Order completion received orderId={} status={}", event.getOrderId(), event.getStatus());
            orders.findById(UUID.fromString(event.getOrderId().toString())).ifPresent(order -> {
                if (event.getStatus() == OrderStatus.CONFIRMED) {
                    order.confirm();
                } else {
                    order.reject();
                }
            });
        }
    }
}
