package com.futspring.backend.domain.notification;

import com.futspring.backend.shared.exception.AppException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PushEndpointPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://fcm.googleapis.com/fcm/send/abc:APA91b",
            "https://android.googleapis.com/gcm/send/abc",
            "https://web.push.apple.com/QGuQyavXutnMH8hVWxR",
            "https://updates.push.services.mozilla.com/wpush/v2/gAAAAAB",
            "https://wns2-db5p.notify.windows.com/w/?token=BQYAAAB",
            "https://FCM.GoogleApis.com/fcm/send/abc",
            "https://fcm.googleapis.com:443/fcm/send/abc"})
    void acceptsTheKnownPushServices(String endpoint) {
        assertThat(PushEndpointPolicy.allowedEndpoint(endpoint)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fcm.googleapis.com/fcm/send/abc",
            "https://fcm.googleapis.com:8443/fcm/send/abc",
            "https://user:pass@fcm.googleapis.com/fcm/send/abc",
            "https://push.apple.com.evil.com/x",
            "https://evilpush.apple.com/x",
            "https://push.apple.com/x",
            "https://fcm.googleapis.com.evil.com/x",
            "https://storage.googleapis.com/bucket",
            "https://evil.com/fcm.googleapis.com",
            "https://127.0.0.1/x",
            "https://169.254.169.254/latest/meta-data",
            "https://[::1]/x",
            "https://localhost/x",
            "ftp://fcm.googleapis.com/x",
            "fcm.googleapis.com/fcm/send/abc",
            "not a url",
            ""})
    void rejectsAnythingElse(String endpoint) {
        assertThat(PushEndpointPolicy.allowedEndpoint(endpoint)).isFalse();
    }

    @Test
    void rejectsANullEndpoint() {
        assertThat(PushEndpointPolicy.allowedEndpoint(null)).isFalse();
    }

    @Test
    void acceptsRealKeysWithOrWithoutPadding() {
        String p256dh = PushTestKeys.p256dh();
        String auth = PushTestKeys.auth();
        PushEndpointPolicy.requireValid("https://fcm.googleapis.com/x", p256dh, auth);
        PushEndpointPolicy.requireValid("https://fcm.googleapis.com/x", p256dh + "=", auth + "==");
    }

    @Test
    void rejectsKeysOfTheWrongSizeOrFormat() {
        String endpoint = "https://fcm.googleapis.com/x";
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        byte[] compressed = new byte[33];
        compressed[0] = 0x02;
        byte[] notAPoint = new byte[65];
        notAPoint[0] = 0x05;

        assertInvalidKeys(endpoint, b64.encodeToString(compressed), PushTestKeys.auth());
        assertInvalidKeys(endpoint, b64.encodeToString(notAPoint), PushTestKeys.auth());
        assertInvalidKeys(endpoint, PushTestKeys.p256dh(), b64.encodeToString(new byte[15]));
        assertInvalidKeys(endpoint, PushTestKeys.p256dh(), b64.encodeToString(new byte[17]));
        assertInvalidKeys(endpoint, "not*base64", PushTestKeys.auth());
    }

    @Test
    void anEndpointOutsideTheAllowlistIsA400() {
        assertThatThrownBy(() -> PushEndpointPolicy.requireValid("https://evil.com/x", PushTestKeys.p256dh(),
                PushTestKeys.auth()))
                .isInstanceOf(AppException.class)
                .hasMessage("Endpoint de notificação inválido")
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static void assertInvalidKeys(String endpoint, String p256dh, String auth) {
        assertThatThrownBy(() -> PushEndpointPolicy.requireValid(endpoint, p256dh, auth))
                .isInstanceOf(AppException.class)
                .hasMessage("Chaves de notificação inválidas");
    }
}
