package com.eventflow.webhook;

import com.eventflow.webhook.delivery.WebhookDeliveryService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSignatureTest {

    @Test
    void knownVector() {
        // HMAC-SHA256("secret", "hello") = known hex
        String sig = WebhookDeliveryService.hmacSha256("secret", "hello");
        assertThat(sig).hasSize(64); // 32 bytes → 64 hex chars
        assertThat(sig).isEqualTo("88aab3ede8d3adf94d26ab90d3bafd4a2083070c3bcce9c014ee04a443847c0b");
    }

    @Test
    void differentSecretsProduceDifferentSignatures() {
        String s1 = WebhookDeliveryService.hmacSha256("secret1", "payload");
        String s2 = WebhookDeliveryService.hmacSha256("secret2", "payload");
        assertThat(s1).isNotEqualTo(s2);
    }

    @Test
    void differentPayloadsProduceDifferentSignatures() {
        String s1 = WebhookDeliveryService.hmacSha256("secret", "payload1");
        String s2 = WebhookDeliveryService.hmacSha256("secret", "payload2");
        assertThat(s1).isNotEqualTo(s2);
    }
}
