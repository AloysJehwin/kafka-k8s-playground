package com.eventflow.webbff.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds per-customerId SSE emitters. When orders.completed events arrive,
 * the Kafka listener calls broadcast(customerId, payload).
 *
 * Multiple browser tabs per user supported via CopyOnWriteArrayList.
 */
@Component
public class OrderEventBroker {

    private static final Logger log = LoggerFactory.getLogger(OrderEventBroker.class);
    private static final long TIMEOUT_MS = 30L * 60 * 1000; // 30 min

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String customerId) {
        var emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(customerId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(customerId, emitter));
        emitter.onTimeout(() -> remove(customerId, emitter));
        emitter.onError(e -> remove(customerId, emitter));

        try {
            emitter.send(SseEmitter.event().name("hello").data(Map.of("customerId", customerId)));
        } catch (IOException ignored) {}

        log.info("SSE subscribed customerId={}", customerId);
        return emitter;
    }

    public void broadcast(String customerId, Object payload) {
        var list = emitters.get(customerId);
        if (list == null) return;
        for (var emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("order-completed").data(payload));
            } catch (IOException e) {
                log.warn("SSE send failed customerId={} — removing", customerId);
                remove(customerId, emitter);
            }
        }
    }

    private void remove(String customerId, SseEmitter emitter) {
        var list = emitters.get(customerId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(customerId);
        }
    }
}
