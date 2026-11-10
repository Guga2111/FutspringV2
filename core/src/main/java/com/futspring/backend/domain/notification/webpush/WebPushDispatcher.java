package com.futspring.backend.domain.notification.webpush;

import com.futspring.backend.domain.daily.attendance.AttendanceWindow;
import com.futspring.backend.domain.notification.Notification;
import com.futspring.backend.domain.notification.NotificationCategory;
import com.futspring.backend.domain.notification.NotificationContent;
import com.futspring.backend.domain.notification.NotificationRepository;
import com.futspring.backend.domain.notification.NotificationType;
import com.futspring.backend.domain.notification.preference.NotificationPreferenceService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Sends committed notifications as Web Push to every subscribed device of the recipient: the type is pushable and the
 * recipient's push preference for the category is on (row or default). Runs on Boot's default async executor
 * (virtual threads), like the STOMP push; push services have no quota like Resend's, so there is no pacing. Four
 * queries whatever the size, in a short read-only transaction; the sending runs outside it. 201 marks the
 * subscription alive, 404/410 deletes it, anything else is only logged (no retry). Metric futspring.webpush.sent
 * (result=ok|gone|failed).
 */
@Slf4j
@Component
public class WebPushDispatcher {

    static final String METRIC = "futspring.webpush.sent";
    static final Duration DEFAULT_TTL = Duration.ofHours(24);
    static final Duration MIN_TTL = Duration.ofHours(1);

    private final NotificationRepository notificationRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final NotificationPreferenceService preferenceService;
    private final WebPushSender sender;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final TransactionOperations readOnlyTransaction;
    private final TransactionOperations writeTransaction;
    private final Counter sentOk;
    private final Counter sentGone;
    private final Counter sentFailed;

    // The JSON the service worker reads: the notification's title and body, where a tap goes, the tag that
    // replaces a notification already shown for the same session, and the bell's count for the app badge
    record Payload(String title, String body, String url, String tag, long unreadCount) {
    }

    record OutgoingPush(Long notificationId, PushSubscription subscription, byte[] payload,
                        WebPushSender.Options options) {
    }

    @Autowired
    public WebPushDispatcher(NotificationRepository notificationRepository,
                             PushSubscriptionRepository subscriptionRepository,
                             NotificationPreferenceService preferenceService, WebPushSender sender,
                             JsonMapper jsonMapper, Clock clock, PlatformTransactionManager transactionManager,
                             MeterRegistry meterRegistry) {
        this(notificationRepository, subscriptionRepository, preferenceService, sender, jsonMapper, clock,
                readOnly(transactionManager), new TransactionTemplate(transactionManager), meterRegistry);
    }

    WebPushDispatcher(NotificationRepository notificationRepository,
                      PushSubscriptionRepository subscriptionRepository,
                      NotificationPreferenceService preferenceService, WebPushSender sender, JsonMapper jsonMapper,
                      Clock clock, TransactionOperations readOnlyTransaction, TransactionOperations writeTransaction,
                      MeterRegistry meterRegistry) {
        this.notificationRepository = notificationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.preferenceService = preferenceService;
        this.sender = sender;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.readOnlyTransaction = readOnlyTransaction;
        this.writeTransaction = writeTransaction;
        this.sentOk = counter(meterRegistry, "ok");
        this.sentGone = counter(meterRegistry, "gone");
        this.sentFailed = counter(meterRegistry, "failed");
    }

    // Called after the commit; never makes the request that caused the notifications wait
    @Async
    public void dispatch(List<Long> notificationIds) {
        if (sender.enabled()) {
            send(notificationIds);
        }
    }

    void send(List<Long> notificationIds) {
        List<OutgoingPush> pushes;
        try {
            pushes = readOnlyTransaction.execute(status -> build(notificationIds));
        } catch (RuntimeException e) {
            log.warn("Could not build the web pushes of notifications {}", notificationIds, e);
            return;
        }
        List<Long> alive = new ArrayList<>();
        List<Long> gone = new ArrayList<>();
        for (OutgoingPush push : pushes == null ? List.<OutgoingPush>of() : pushes) {
            try {
                int status = sender.send(push.subscription(), push.payload(), push.options());
                record(push, status, alive, gone);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (IOException | RuntimeException e) {
                // one device's failure doesn't stop the others
                sentFailed.increment();
                log.warn("Could not web push notification {} to subscription {}", push.notificationId(),
                        push.subscription().getId(), e);
            }
        }
        if (!alive.isEmpty() || !gone.isEmpty()) {
            try {
                writeTransaction.executeWithoutResult(status -> {
                    if (!alive.isEmpty()) {
                        subscriptionRepository.markSuccess(alive, Instant.now(clock));
                    }
                    if (!gone.isEmpty()) {
                        subscriptionRepository.deleteByIdIn(gone);
                    }
                });
            } catch (RuntimeException e) {
                log.warn("Could not update the web push subscriptions after a dispatch", e);
            }
        }
    }

    private void record(OutgoingPush push, int status, List<Long> alive, List<Long> gone) {
        Long subscriptionId = push.subscription().getId();
        if (status >= 200 && status < 300) {
            sentOk.increment();
            alive.add(subscriptionId);
        } else if (status == 404 || status == 410) {
            // the browser unsubscribed or the subscription expired: it never comes back
            sentGone.increment();
            gone.add(subscriptionId);
        } else if (status == 400 || status == 413) {
            sentFailed.increment();
            log.error("Push service rejected notification {} for subscription {} with {} (malformed request)",
                    push.notificationId(), subscriptionId, status);
        } else {
            sentFailed.increment();
            log.warn("Push service answered {} for notification {}, subscription {}", status,
                    push.notificationId(), subscriptionId);
        }
    }

    // Four queries whatever the size: the rows, the recipients' subscriptions, their preferences, their unread counts
    private List<OutgoingPush> build(List<Long> notificationIds) {
        List<Notification> pushable = notificationRepository.findAllByIdInWithRefs(notificationIds).stream()
                .filter(n -> n.getType().pushable())
                .toList();
        if (pushable.isEmpty()) {
            return List.of();
        }
        List<Long> recipientIds = pushable.stream().map(n -> n.getRecipient().getId()).distinct().toList();
        Map<Long, List<PushSubscription>> devices = subscriptionRepository.findByUserIdIn(recipientIds).stream()
                .collect(Collectors.groupingBy(s -> s.getUser().getId()));
        if (devices.isEmpty()) {
            return List.of();
        }
        List<Long> subscribed = recipientIds.stream().filter(devices::containsKey).toList();
        Map<Long, Map<NotificationCategory, Boolean>> enabled = preferenceService.pushEnabled(subscribed);
        Map<Long, Long> unread = notificationRepository.countUnreadByRecipients(subscribed).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        List<OutgoingPush> pushes = new ArrayList<>();
        for (Notification n : pushable) {
            Long recipientId = n.getRecipient().getId();
            if (!devices.containsKey(recipientId) || !enabled.get(recipientId).get(n.getType().category())) {
                continue;
            }
            String tag = tag(n);
            NotificationContent content = NotificationContent.of(n);
            byte[] payload = jsonMapper.writeValueAsString(new Payload(content.title(), content.pushBody(),
                    url(content, n), tag, unread.getOrDefault(recipientId, 0L))).getBytes(StandardCharsets.UTF_8);
            WebPushSender.Options options = new WebPushSender.Options(ttl(n), urgency(n), topic(tag));
            devices.get(recipientId).forEach(s -> pushes.add(new OutgoingPush(n.getId(), s, payload, options)));
        }
        return pushes;
    }

    // ?notificacao= makes the app mark it read when it opens (the service worker has no token)
    static String url(NotificationContent content, Notification n) {
        return UriComponentsBuilder.fromPath(content.ctaPath()).queryParam("notificacao", n.getId()).toUriString();
    }

    // One per session (else pelada): a collapsed TEAMS_SORTED / SESSION_FINISHED replaces the one already shown
    static String tag(Notification n) {
        Long scope = n.getDaily() != null ? n.getDaily().getId()
                : n.getPelada() != null ? n.getPelada().getId() : n.getId();
        return n.getType().name() + "-" + scope;
    }

    // The Topic header allows at most 32 URL-safe base64 characters, and a tag can be longer: 24 bytes of its SHA-256
    static String topic(String tag) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(tag.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(hash, 24));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // A reminder or an opening is useless after the session starts; everything else waits up to a day for the device
    Duration ttl(Notification n) {
        boolean untilStart = n.getType() == NotificationType.SESSION_REMINDER
                || n.getType() == NotificationType.DIARISTA_WINDOW_OPEN;
        if (!untilStart || n.getDaily() == null) {
            return DEFAULT_TTL;
        }
        Duration left = Duration.between(Instant.now(clock), AttendanceWindow.startsAt(n.getDaily()));
        return left.compareTo(MIN_TTL) < 0 ? MIN_TTL : left.compareTo(DEFAULT_TTL) > 0 ? DEFAULT_TTL : left;
    }

    static WebPushSender.Urgency urgency(Notification n) {
        return n.getType().category() == NotificationCategory.ATTENDANCE
                ? WebPushSender.Urgency.HIGH : WebPushSender.Urgency.NORMAL;
    }

    private static Counter counter(MeterRegistry meterRegistry, String result) {
        return Counter.builder(METRIC)
                .description("Web Push messages handed to the push services")
                .tag("result", result)
                .register(meterRegistry);
    }

    private static TransactionOperations readOnly(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }
}
