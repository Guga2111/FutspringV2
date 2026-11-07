package com.futspring.backend.domain.notification;

import com.zerodeplibs.webpush.VAPIDKeyPair;
import com.zerodeplibs.webpush.httpclient.StandardHttpClientRequestPreparer;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Encrypts (aes128gcm) and signs (VAPID ES256) with zerodep-web-push-java, which only builds the HttpRequest; the
 * JDK HttpClient sends it. The client never follows redirects (the endpoint was checked by PushEndpointPolicy and a
 * redirect could point anywhere) and has timeouts, so a slow push service never holds a thread forever.
 */
@RequiredArgsConstructor
class VapidWebPushSender implements WebPushSender {

    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    // The push services accept a JWT valid for at most 24 h
    private static final int JWT_HOURS = 12;

    private final HttpClient httpClient;
    private final VAPIDKeyPair keyPair;
    private final String subject;

    static HttpClient httpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public int send(PushSubscription subscription, byte[] payload, Options options)
            throws IOException, InterruptedException {
        com.zerodeplibs.webpush.PushSubscription target = new com.zerodeplibs.webpush.PushSubscription();
        target.setEndpoint(subscription.getEndpoint());
        com.zerodeplibs.webpush.PushSubscription.Keys keys = new com.zerodeplibs.webpush.PushSubscription.Keys();
        keys.setP256dh(subscription.getP256dh());
        keys.setAuth(subscription.getAuth());
        target.setKeys(keys);

        var builder = StandardHttpClientRequestPreparer.getBuilder()
                .pushSubscription(target)
                .vapidJWTExpiresAfter(JWT_HOURS, TimeUnit.HOURS)
                .vapidJWTSubject(subject)
                .pushMessage(payload)
                .ttl(options.ttl().toSeconds(), TimeUnit.SECONDS)
                .topic(options.topic());
        builder = options.urgency() == Urgency.HIGH ? builder.urgencyHigh() : builder.urgencyNormal();
        HttpRequest request = builder.build(keyPair).toRequestBuilder().timeout(REQUEST_TIMEOUT).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
