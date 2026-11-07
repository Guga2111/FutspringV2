package com.futspring.backend.domain.notification;

import java.io.IOException;
import java.time.Duration;

// Sends one encrypted Web Push message (RFC 8030/8291/8292). Chosen in WebPushConfig: VAPID, or a no-op without a key
public interface WebPushSender {

    // The push service's HTTP status (201 = accepted, 404/410 = subscription gone)
    int send(PushSubscription subscription, byte[] payload, Options options) throws IOException, InterruptedException;

    // False for the no-op sender, so callers skip building messages nobody will send
    default boolean enabled() {
        return true;
    }

    enum Urgency { NORMAL, HIGH }

    // topic: at most 32 URL-safe base64 characters; a pending message with the same topic is replaced
    record Options(Duration ttl, Urgency urgency, String topic) {
    }
}
