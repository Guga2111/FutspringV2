package com.futspring.backend.domain.subscription;

import com.futspring.backend.domain.chat.ChatService;
import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.daily.attendance.DailyAttendance;
import com.futspring.backend.domain.daily.DailyStatus;
import com.futspring.backend.domain.daily.team.Team;
import com.futspring.backend.domain.daily.attendance.DailyAttendanceRepository;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.domain.daily.team.TeamRepository;
import com.futspring.backend.domain.finance.account.FinanceManager;
import com.futspring.backend.domain.finance.account.FinanceManagerRepository;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * A read-only pelada (subscription) answers 402 on every command that writes sporting or financial data, and
 * keeps everything else (chat, settings, members, invitations, deletion) working; an exempt one writes.
 */
class SubscriptionReadOnlyTest extends BaseIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private DailyRepository dailyRepository;
    @Autowired private DailyAttendanceRepository attendanceRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private PeladaSubscriptionRepository subscriptionRepository;
    @Autowired private FinanceManagerRepository financeManagerRepository;
    @Autowired private ChatService chatService;
    @Autowired private EntityManager entityManager;

    private User admin;
    private User member;
    private User outsider;
    private Pelada pelada;
    private Daily daily;
    private Team team1;
    private Team team2;
    private PeladaSubscription subscription;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder().email("ro-admin@test.com").username("roadmin").password("hash").build());
        member = userRepository.save(User.builder().email("ro-member@test.com").username("romember").password("hash").build());
        outsider = userRepository.save(User.builder().email("ro-out@test.com").username("roout").password("hash").build());
        pelada = peladaRepository.save(Pelada.builder().name("Pelada travada").dayOfWeek("FRIDAY").timeOfDay("18:00")
                .duration(2f).creator(admin).members(new HashSet<>(Set.of(admin, member)))
                .admins(new HashSet<>(Set.of(admin))).numberOfTeams(2).playersPerTeam(5).build());
        daily = dailyRepository.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.now().plusDays(7))
                .dailyTime("18:00").status(DailyStatus.SCHEDULED).build());
        attendanceRepository.save(new DailyAttendance(daily, member, Instant.now()));
        team1 = teamRepository.save(Team.builder().daily(daily).name("Time 1").players(new HashSet<>(Set.of(admin))).build());
        team2 = teamRepository.save(Team.builder().daily(daily).name("Time 2").players(new HashSet<>(Set.of(member))).build());
        FinanceManager manager = new FinanceManager();
        manager.setPeladaId(pelada.getId());
        manager.setUserId(admin.getId());
        financeManagerRepository.save(manager);
        subscription = subscriptionRepository.save(
                new PeladaSubscription(pelada, SubscriptionKind.PENDING, null, Instant.now()));
    }

    // --- locked: every command that writes sporting or financial data ---

    static Stream<Arguments> lockedCommands() {
        return Stream.of(
                locked("criar sessão", t -> t.asAdmin(post("/api/v1/peladas/" + t.pelada.getId() + "/dailies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dailyDate\":\"" + LocalDate.now().plusDays(14) + "\",\"dailyTime\":\"20:00\"}"))),
                locked("mudar status", t -> t.asAdmin(json(put(t.dailyPath("/status")), "{\"status\":\"CONFIRMED\"}"))),
                locked("apagar sessão", t -> t.asAdmin(delete(t.dailyPath("")))),
                locked("importar da mensagem", t -> t.asAdmin(json(post(t.dailyPath("/populate")),
                        "{\"teams\":[{\"colorName\":\"Azul\",\"players\":[{\"userId\":" + t.admin.getId() + "}]}]}"))),
                locked("foto do campeão", t -> t.asAdmin(multipart(t.dailyPath("/champion-image"))
                        .file(new MockMultipartFile("file", "c.png", "image/png", new byte[]{1}))
                        .with(request -> { request.setMethod("PUT"); return request; }))),
                locked("confirmar presença", t -> t.asMember(post(t.dailyPath("/confirm")))),
                locked("admin confirma", t -> t.asAdmin(post(t.dailyPath("/confirm/" + t.admin.getId())))),
                locked("confirmar todos", t -> t.asAdmin(post(t.dailyPath("/confirm/all")))),
                locked("sortear times", t -> t.asAdmin(post(t.dailyPath("/sort-teams")))),
                locked("trocar jogadores", t -> t.asAdmin(json(put(t.dailyPath("/teams/swap")),
                        "{\"player1Id\":" + t.admin.getId() + ",\"player2Id\":" + t.member.getId() + "}"))),
                locked("salvar times", t -> t.asAdmin(json(put(t.dailyPath("/teams")),
                        "{\"teams\":[{\"name\":\"A\",\"playerIds\":[" + t.member.getId() + "]},"
                                + "{\"name\":\"B\",\"playerIds\":[" + t.member.getId() + "]}]}"))),
                locked("nome do time", t -> t.asAdmin(json(patch(t.dailyPath("/teams/" + t.team1.getId() + "/name")),
                        "{\"name\":\"Novo\"}"))),
                locked("cor do time", t -> t.asAdmin(json(patch(t.dailyPath("/teams/" + t.team1.getId() + "/color")),
                        "{\"color\":\"#123456\"}"))),
                locked("resultados", t -> t.asAdmin(json(post(t.dailyPath("/results")),
                        "[{\"team1Id\":" + t.team1.getId() + ",\"team2Id\":" + t.team2.getId()
                                + ",\"team1Score\":1,\"team2Score\":0}]"))),
                locked("finalizar", t -> t.asAdmin(json(post(t.dailyPath("/finalize")), "{}"))),
                locked("configurações do financeiro", t -> t.asAdmin(json(put(t.financePath("/settings")),
                        "{\"command\":" + command() + ",\"visibility\":\"INDIVIDUAL\"}"))),
                locked("informar pagamento", t -> t.asMember(multipart(t.financePath("/lists/1/charges/1/report"))
                        .file(new MockMultipartFile("command", "", MediaType.APPLICATION_JSON_VALUE,
                                command().getBytes())))),
                // not an image: answered 402 before the upload is decoded (else 400)
                locked("comprovante", t -> t.asMember(multipart(t.financePath("/lists/1/charges/1/report"))
                        .file(new MockMultipartFile("command", "", MediaType.APPLICATION_JSON_VALUE,
                                command().getBytes()))
                        .file(new MockMultipartFile("file", "r.png", "image/png", new byte[]{1})))),
                locked("QR do financeiro", t -> t.asAdmin(multipart(t.financePath("/qr"))
                        .file(new MockMultipartFile("command", "", MediaType.APPLICATION_JSON_VALUE,
                                command().getBytes()))
                        .file(new MockMultipartFile("file", "q.png", "image/png", new byte[]{1})))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("lockedCommands")
    void readOnlyPelada_answers402(Function<SubscriptionReadOnlyTest, RequestBuilder> request)
            throws Exception {
        long dailies = dailyRepository.count();

        mockMvc.perform(request.apply(this))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.status").value(402))
                .andExpect(jsonPath("$.message").value(SubscriptionGuard.READ_ONLY_MESSAGE));

        assertThat(dailyRepository.count()).isEqualTo(dailies);
        assertThat(attendanceRepository.findUsersByDaily(daily)).containsExactly(member);
    }

    @Test
    void financeRetryOfACommandDoneBeforeTheLock_answersItsResult() throws Exception {
        subscription.setKind(SubscriptionKind.EXEMPT);
        String body = "{\"command\":" + command() + ",\"visibility\":\"TRANSPARENT\"}";
        mockMvc.perform(asAdmin(json(put(financePath("/settings")), body))).andExpect(status().isOk());

        subscription.setKind(SubscriptionKind.PENDING);
        mockMvc.perform(asAdmin(json(put(financePath("/settings")), body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overview.settings.visibility").value("TRANSPARENT"));
    }

    @Test
    void expiredTrial_isReadOnlyToo() throws Exception {
        subscription.setKind(SubscriptionKind.TRIAL);
        subscription.setValidUntil(Instant.now().minusSeconds(60));

        mockMvc.perform(asAdmin(post(dailyPath("/sort-teams"))))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void exemptPelada_writes() throws Exception {
        subscription.setKind(SubscriptionKind.EXEMPT);

        mockMvc.perform(asAdmin(json(post("/api/v1/peladas/" + pelada.getId() + "/dailies"),
                        "{\"dailyDate\":\"" + LocalDate.now().plusDays(14) + "\",\"dailyTime\":\"20:00\"}")))
                .andExpect(status().isCreated());
        mockMvc.perform(asAdmin(post(dailyPath("/confirm/" + admin.getId()))))
                .andExpect(status().isOk());
    }

    // --- free: reads, chat, settings, members, invitations, bumps, deletion ---

    @Test
    void readOnlyPelada_stillReads() throws Exception {
        mockMvc.perform(asMember(get(dailyPath("")))).andExpect(status().isOk());
        mockMvc.perform(asMember(get("/api/v1/peladas/" + pelada.getId()))).andExpect(status().isOk());
    }

    @Test
    void readOnlyPelada_stillChats() {
        assertThat(chatService.saveAndBroadcast(pelada.getId(), member.getEmail(), "Bora renovar?").content())
                .isEqualTo("Bora renovar?");
    }

    @Test
    void readOnlyPelada_stillChangesSettingsAndMembers() throws Exception {
        mockMvc.perform(asAdmin(json(put("/api/v1/peladas/" + pelada.getId()), "{\"name\":\"Novo nome\"}")))
                .andExpect(status().isOk());
        mockMvc.perform(asAdmin(json(put("/api/v1/peladas/" + pelada.getId() + "/players/" + member.getId() + "/admin"),
                        "{\"isAdmin\":true}")))
                .andExpect(status().isOk());
        mockMvc.perform(asMember(post(dailyPath("/bumps/seen")))).andExpect(status().isNoContent());
    }

    @Test
    void readOnlyPelada_stillLetsPlayersLeaveAndTheSessionBeCanceled() throws Exception {
        attendanceRepository.save(new DailyAttendance(daily, admin, Instant.now()));

        mockMvc.perform(asMember(delete(dailyPath("/confirm")))).andExpect(status().isOk());
        mockMvc.perform(asAdmin(delete(dailyPath("/confirm/" + admin.getId())))).andExpect(status().isOk());
        mockMvc.perform(asAdmin(json(put(dailyPath("/status")), "{\"status\":\"CANCELED\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));
        assertThat(attendanceRepository.findUsersByDaily(daily)).isEmpty();
    }

    @Test
    void readOnlyPelada_stillInvites() throws Exception {
        mockMvc.perform(asAdmin(json(post("/api/v1/peladas/" + pelada.getId() + "/invitations"),
                        "{\"userId\":" + outsider.getId() + "}")))
                .andExpect(status().isOk());
    }

    @Test
    void readOnlyPelada_canBeDeleted() throws Exception {
        // a request starts with an empty persistence context; the bulk deletes don't detach what setUp left managed
        entityManager.flush();
        entityManager.clear();
        mockMvc.perform(asAdmin(delete("/api/v1/peladas/" + pelada.getId()))).andExpect(status().isNoContent());

        assertThat(peladaRepository.findById(pelada.getId())).isEmpty();
        assertThat(subscriptionRepository.findByPeladaId(pelada.getId())).isEmpty();
    }

    private static Arguments locked(String name,
                                    Function<SubscriptionReadOnlyTest, RequestBuilder> request) {
        return Arguments.of(Named.of(name, request));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String command() {
        return "{\"revision\":0,\"requestKey\":\"" + UUID.randomUUID() + "\"}";
    }

    private String dailyPath(String suffix) {
        return "/api/v1/dailies/" + daily.getId() + suffix;
    }

    private String financePath(String suffix) {
        return "/api/v1/peladas/" + pelada.getId() + "/finance" + suffix;
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> B asAdmin(B request) {
        return request.header("Authorization", bearerToken(admin.getId(), admin.getEmail()));
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> B asMember(B request) {
        return request.header("Authorization", bearerToken(member.getId(), member.getEmail()));
    }
}
