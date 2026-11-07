package com.futspring.backend.domain.notification;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebPushConfigTest {

    @Test
    void withoutAPrivateKeyPushIsOff() {
        WebPushConfig config = new WebPushConfig("", " ", "mailto:a@b.c");

        assertThat(config.configured()).isFalse();
        assertThat(config.publicKey()).isEmpty();
        assertThat(config.webPushSender().enabled()).isFalse();
    }

    @Test
    void aRawBase64UrlPairBuildsTheVapidSender() {
        KeyPair pair = PushTestKeys.generate();
        WebPushConfig config = new WebPushConfig(PushTestKeys.publicKey(pair), PushTestKeys.privateKey(pair), "mailto:a@b.c");

        assertThat(config.publicKey()).contains(PushTestKeys.publicKey(pair));
        assertThat(config.webPushSender()).isInstanceOf(VapidWebPushSender.class);
        assertThat(WebPushConfig.keyPair(PushTestKeys.publicKey(pair), PushTestKeys.privateKey(pair))
                .extractPublicKeyInUncompressedForm()).hasSize(65);
    }

    @Test
    void aMismatchedOrMalformedPairFailsTheStartup() {
        KeyPair one = PushTestKeys.generate();
        KeyPair other = PushTestKeys.generate();

        assertThatThrownBy(() -> new WebPushConfig(PushTestKeys.publicKey(other), PushTestKeys.privateKey(one), "mailto:a@b.c")
                .webPushSender())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebPushConfig("", PushTestKeys.privateKey(one), "mailto:a@b.c").webPushSender())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebPushConfig("abc", "!!", "mailto:a@b.c").webPushSender())
                .isInstanceOf(IllegalStateException.class);
    }
}
