package com.futspring.backend.domain.notification;

import com.futspring.backend.domain.mail.EmailSender;
import com.futspring.backend.domain.mail.MailConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * E-mails committed notifications: the type is emailable, the recipient's category is on (row or default) and a
 * real EmailSender is configured. Runs on its own single thread with a bounded queue, paced by EmailPacer; never on
 * the default @Async pool, whose several threads would break the pacing. The executor is owned here, not a bean:
 * an Executor bean would replace Spring Boot's default async executor. A full queue or a failed send is only
 * logged (no retry); the in-app notification is already stored.
 */
@Slf4j
@Component
public class NotificationEmailDispatcher {

    static final int QUEUE_CAPACITY = 500;
    static final String UNSUBSCRIBE_PATH = "/api/v1/notifications/unsubscribe";

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;
    private final UnsubscribeTokens unsubscribeTokens;
    private final EmailSender emailSender;
    private final MailConfig mailConfig;
    private final TransactionOperations readOnlyTransaction;
    private final EmailPacer pacer;
    private final Executor executor;

    record OutgoingEmail(Long notificationId, Long recipientId, String to, String subject, String html,
                         Map<String, String> headers) { }

    @Autowired
    public NotificationEmailDispatcher(NotificationRepository notificationRepository,
                                       NotificationPreferenceService preferenceService,
                                       UnsubscribeTokens unsubscribeTokens, EmailSender emailSender,
                                       MailConfig mailConfig, PlatformTransactionManager transactionManager,
                                       EmailPacer pacer) {
        this(notificationRepository, preferenceService, unsubscribeTokens, emailSender, mailConfig,
                readOnly(transactionManager), pacer, singleThreadExecutor());
    }

    NotificationEmailDispatcher(NotificationRepository notificationRepository,
                                NotificationPreferenceService preferenceService,
                                UnsubscribeTokens unsubscribeTokens, EmailSender emailSender, MailConfig mailConfig,
                                TransactionOperations readOnlyTransaction, EmailPacer pacer, Executor executor) {
        this.notificationRepository = notificationRepository;
        this.preferenceService = preferenceService;
        this.unsubscribeTokens = unsubscribeTokens;
        this.emailSender = emailSender;
        this.mailConfig = mailConfig;
        this.readOnlyTransaction = readOnlyTransaction;
        this.pacer = pacer;
        this.executor = executor;
    }

    // Called after the commit, on the request's thread: only queues the work
    public void dispatch(List<Long> notificationIds) {
        if (!emailSender.enabled()) {
            return;
        }
        try {
            executor.execute(() -> send(notificationIds));
        } catch (TaskRejectedException e) {
            log.warn("E-mail queue full: notifications {} were not e-mailed", notificationIds);
        }
    }

    // The e-mails are built in a short read-only transaction; the paced sending runs outside it
    void send(List<Long> notificationIds) {
        List<OutgoingEmail> emails;
        try {
            emails = readOnlyTransaction.execute(status -> build(notificationIds));
        } catch (RuntimeException e) {
            log.warn("Could not build the e-mails of notifications {}", notificationIds, e);
            return;
        }
        for (OutgoingEmail email : emails == null ? List.<OutgoingEmail>of() : emails) {
            try {
                pacer.acquire();
                emailSender.send(email.to(), email.subject(), email.html(), List.of(NotificationEmail.LOGO),
                        email.headers());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // one recipient's failure doesn't stop the others
                log.warn("Could not e-mail notification {} to user {}", email.notificationId(), email.recipientId(), e);
            }
        }
    }

    // Two queries whatever the size: the rows with their references, the recipients' preferences
    private List<OutgoingEmail> build(List<Long> notificationIds) {
        List<Notification> emailable = notificationRepository.findAllByIdInWithRefs(notificationIds).stream()
                .filter(n -> n.getType().emailable())
                .toList();
        if (emailable.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<NotificationCategory, Boolean>> enabled = preferenceService.emailEnabled(
                emailable.stream().map(n -> n.getRecipient().getId()).distinct().toList());
        return emailable.stream()
                .filter(n -> enabled.get(n.getRecipient().getId()).get(n.getType().category()))
                .map(this::toEmail)
                .toList();
    }

    private OutgoingEmail toEmail(Notification n) {
        NotificationCategory category = n.getType().category();
        NotificationEmailContent content = NotificationEmailContent.of(n);
        String token = unsubscribeTokens.create(n.getRecipient().getId(), category);
        String appUrl = mailConfig.getFrontendUrl();
        String html = NotificationEmail.html(content, category, appUrl,
                UriComponentsBuilder.fromUriString(appUrl).path(content.ctaPath()).toUriString(),
                UriComponentsBuilder.fromUriString(appUrl).path("/settings/notifications").toUriString(),
                UriComponentsBuilder.fromUriString(appUrl).path("/notifications/unsubscribe")
                        .queryParam("token", token).toUriString());
        String oneClick = UriComponentsBuilder.fromUriString(mailConfig.getApiUrl()).path(UNSUBSCRIBE_PATH)
                .queryParam("token", token).toUriString();
        return new OutgoingEmail(n.getId(), n.getRecipient().getId(), n.getRecipient().getEmail(), content.subject(),
                html, Map.of(
                        "List-Unsubscribe", "<" + oneClick + ">",
                        "List-Unsubscribe-Post", "List-Unsubscribe=One-Click"));
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ThreadPoolTaskExecutor pool) {
            pool.shutdown();
        }
    }

    private static TransactionOperations readOnly(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    private static ThreadPoolTaskExecutor singleThreadExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(QUEUE_CAPACITY);
        pool.setThreadNamePrefix("notification-email-");
        pool.initialize();
        return pool;
    }
}
