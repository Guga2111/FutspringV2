package com.futspring.backend.domain.notification.webpush;

// Used when VAPID_PRIVATE_KEY is empty (tests, local runs): push is off and nothing is sent
class NoopWebPushSender implements WebPushSender {

    @Override
    public int send(PushSubscription subscription, byte[] payload, Options options) {
        throw new IllegalStateException("Web Push is not configured");
    }

    @Override
    public boolean enabled() {
        return false;
    }
}
