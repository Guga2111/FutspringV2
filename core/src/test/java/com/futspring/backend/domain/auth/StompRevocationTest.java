package com.futspring.backend.domain.auth;

import com.futspring.backend.domain.auth.passwordreset.PasswordResetService;
import com.futspring.backend.domain.auth.passwordreset.PasswordResetToken;
import com.futspring.backend.domain.auth.passwordreset.PasswordResetTokenRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.pelada.PeladaService;
import com.futspring.backend.domain.user.AccountDeletionService;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.domain.user.UserService;
import com.futspring.backend.domain.user.dto.ChangePasswordRequestDTO;
import com.futspring.backend.shared.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real STOMP connections against the running server, with no test transaction: the revocations only happen after
 * a commit. The data is committed, so each test removes what it created (the H2 database is shared by the contexts).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StompRevocationTest {

    private static final String PASSWORD = "password123";
    private static final long WAIT_SECONDS = 5;

    @LocalServerPort private int port;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private PeladaService peladaService;
    @Autowired private UserService userService;
    @Autowired private AccountDeletionService accountDeletionService;
    @Autowired private PasswordResetService passwordResetService;
    @Autowired private PasswordResetTokenRepository resetTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient stompClient;
    private User admin;
    private User member;
    private Pelada pelada;
    private String topic;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        // every payload as text: the topic sends text/plain, the notifications application/json
        stompClient.setMessageConverter(new StringMessageConverter() {
            @Override
            protected boolean supportsMimeType(MessageHeaders headers) {
                return true;
            }
        });
        admin = user("admin");
        member = user("member");
        pelada = peladaRepository.save(Pelada.builder().name("Pelada STOMP").dayOfWeek("FRIDAY").timeOfDay("20:00").duration(1.5f).address("Quadra")
                .creator(admin).members(new HashSet<>(Set.of(admin, member))).admins(new HashSet<>(Set.of(admin))).build());
        topic = "/topic/pelada/" + pelada.getId();
    }

    @AfterEach
    void cleanUp() {
        sessions.forEach(session -> {
            if (session.isConnected()) session.disconnect();
        });
        stompClient.stop();
        resetTokenRepository.deleteAll();
        peladaService.deletePelada(pelada.getId(), admin.getEmail());
        userRepository.deleteAll(List.of(admin, member));
    }

    @Test
    void removedMember_stopsReceivingTheTopic_butKeepsTheConnectionAndNotifications() throws Exception {
        Client adminClient = connect(admin);
        Client memberClient = connect(member);
        BlockingQueue<String> adminTopic = adminClient.subscribe(topic);
        BlockingQueue<String> memberTopic = memberClient.subscribe(topic);
        BlockingQueue<String> memberNotifications = memberClient.subscribe("/user/queue/notifications");
        awaitSubscribed(adminTopic, memberTopic);
        awaitSubscribed(memberNotifications,
                () -> messagingTemplate.convertAndSendToUser(member.getEmail(), "/queue/notifications", "antes"));

        peladaService.removePlayers(pelada.getId(), List.of(member.getId()), admin.getEmail());

        messagingTemplate.convertAndSend(topic, "depois");
        awaitMessage(adminTopic, "depois"::equals);
        assertNoMessage(memberTopic, "depois"::equals);
        awaitMessage(memberNotifications, body -> body.contains("REMOVED_FROM_PELADA"));
        assertThat(memberClient.session().isConnected()).isTrue();
    }

    @Test
    void failedRemoval_unsubscribesNobody() throws Exception {
        Client memberClient = connect(member);
        BlockingQueue<String> memberTopic = memberClient.subscribe(topic);
        awaitSubscribed(memberTopic);

        // the creator in the batch: 403 and the transaction rolls back
        assertThatThrownBy(() -> peladaService.removePlayers(pelada.getId(),
                List.of(member.getId(), admin.getId()), admin.getEmail())).isInstanceOf(AppException.class);

        messagingTemplate.convertAndSend(topic, "depois");
        awaitMessage(memberTopic, "depois"::equals);
    }

    @Test
    void passwordReset_closesTheUsersConnections() throws Exception {
        Client memberClient = connect(member);
        Client otherDevice = connect(member);
        Client adminClient = connect(admin);
        resetTokenRepository.save(PasswordResetToken.builder().user(member).tokenHash(sha256("raw-token"))
                .expiresAt(LocalDateTime.now().plusMinutes(10)).createdAt(LocalDateTime.now()).build());

        passwordResetService.resetPassword("raw-token", "newpassword123");

        memberClient.awaitClosed();
        otherDevice.awaitClosed();
        assertThat(adminClient.session().isConnected()).isTrue();
    }

    @Test
    void passwordChange_closesTheUsersConnections() throws Exception {
        Client memberClient = connect(member);

        userService.changePassword(new ChangePasswordRequestDTO(PASSWORD, "newpassword123"), member.getEmail());

        memberClient.awaitClosed();
    }

    @Test
    void wrongCurrentPassword_rollsBack_andClosesNothing() throws Exception {
        Client memberClient = connect(member);
        BlockingQueue<String> memberTopic = memberClient.subscribe(topic);
        awaitSubscribed(memberTopic);

        assertThatThrownBy(() -> userService.changePassword(
                new ChangePasswordRequestDTO("wrong-password", "newpassword123"), member.getEmail()))
                .isInstanceOf(AppException.class);

        messagingTemplate.convertAndSend(topic, "depois");
        awaitMessage(memberTopic, "depois"::equals);
        assertThat(memberClient.session().isConnected()).isTrue();
    }

    @Test
    void accountDeletion_closesTheConnectionsOpenedWithTheOriginalEmail() throws Exception {
        Client memberClient = connect(member);

        accountDeletionService.deleteAccount(PASSWORD, member.getEmail());

        memberClient.awaitClosed();
    }

    // SUBSCRIBE frames reach the broker asynchronously: publish until each subscription has received something
    @SafeVarargs
    private void awaitSubscribed(BlockingQueue<String>... queues) throws InterruptedException {
        for (BlockingQueue<String> queue : queues) {
            awaitSubscribed(queue, () -> messagingTemplate.convertAndSend(topic, "antes"));
        }
    }

    private static void awaitSubscribed(BlockingQueue<String> queue, Runnable publish) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (queue.isEmpty()) {
            assertThat(System.nanoTime()).as("subscription registered").isLessThan(deadline);
            publish.run();
            Thread.sleep(100);
        }
    }

    // Skips what is left of the priming messages
    private static void awaitMessage(BlockingQueue<String> queue, Predicate<String> expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            String body = queue.poll(100, TimeUnit.MILLISECONDS);
            if (body != null && expected.test(body)) return;
        }
        throw new AssertionError("expected message not received");
    }

    private static void assertNoMessage(BlockingQueue<String> queue, Predicate<String> unexpected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            String body = queue.poll(100, TimeUnit.MILLISECONDS);
            assertThat(body != null && unexpected.test(body)).as("message received: %s", body).isFalse();
        }
    }

    private Client connect(User user) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtService.generateToken(user.getId(), user.getEmail(), 0));
        Client client = new Client();
        StompSession session = stompClient.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connectHeaders, client).get(WAIT_SECONDS, TimeUnit.SECONDS);
        sessions.add(session);
        client.session = session;
        return client;
    }

    private User user(String name) {
        String unique = name + "-" + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder().username(unique).email(unique + "@stomp.test")
                .password(passwordEncoder.encode(PASSWORD)).build());
    }

    private static String sha256(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class Client extends StompSessionHandlerAdapter {

        private final BlockingQueue<Throwable> closed = new LinkedBlockingQueue<>();
        private StompSession session;

        StompSession session() {
            return session;
        }

        BlockingQueue<String> subscribe(String destination) {
            BlockingQueue<String> received = new LinkedBlockingQueue<>();
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return String.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    received.add((String) payload);
                }
            });
            return received;
        }

        // A connection closed by the server ends in handleTransportError (ConnectionLostException)
        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            closed.add(exception);
        }

        void awaitClosed() throws InterruptedException {
            assertThat(closed.poll(WAIT_SECONDS, TimeUnit.SECONDS)).as("connection closed by the server").isNotNull();
            assertThat(session.isConnected()).isFalse();
        }
    }
}
