package com.eventflow.webbff.api;

import com.eventflow.webbff.sse.OrderEventBroker;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
public class EventStreamController {

    private final OrderEventBroker broker;

    public EventStreamController(OrderEventBroker broker) {
        this.broker = broker;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal OAuth2User user) {
        var customerId = user.<String>getAttribute("sub");
        return broker.subscribe(customerId);
    }
}
