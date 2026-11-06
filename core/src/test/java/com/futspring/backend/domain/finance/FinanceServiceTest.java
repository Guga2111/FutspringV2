package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.finance.dto.*;
import com.futspring.backend.domain.pelada.*;
import com.futspring.backend.domain.pelada.dto.CreatePeladaRequestDTO;
import com.futspring.backend.domain.daily.*;
import com.futspring.backend.domain.daily.entity.*;
import com.futspring.backend.domain.daily.repository.*;
import com.futspring.backend.domain.user.*;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(FinanceServiceTest.Time.class)
class FinanceServiceTest extends BaseIntegrationTest {
    static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    static class MutableClock extends Clock {
        Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    @TestConfiguration static class Time { @Bean @Primary MutableClock financeClock() { return new MutableClock(); } }
    @Autowired MutableClock clock;
    @Autowired FinanceCollectionService collections;
    @Autowired FinanceLedgerService ledger;
    @Autowired FinanceReadService reads;
    @Autowired FinanceAttachmentService attachments;
    @Autowired FinanceReceiptCleanup cleanup;
    @Autowired FinanceFiles files;
    @Autowired FinanceAccountRepository accounts;
    @Autowired FinanceListRepository lists;
    @Autowired FinanceChargeRepository charges;
    @Autowired FinanceNoticeRepository notices;
    @Autowired FinanceMovementRepository movements;
    @Autowired FinanceAuditRepository audits;
    @Autowired FinancePersonRepository people;
    @Autowired FinanceManagerRepository managerRows;
    @Autowired PeladaRepository peladas;
    @Autowired PeladaService peladaService;
    @Autowired UserRepository users;
    @Autowired DailyRepository dailies;
    @Autowired DailyService dailyService;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired com.futspring.backend.support.QueryCounter queryCounter;
    User admin, member, other, outsider;
    Pelada pelada;
    @BeforeEach void setup() {
        clock.now = NOW;
        admin = user("Gestor"); member = user("Mensalista"); other = user("Diarista"); outsider = user("Visitante");
        pelada = peladas.save(Pelada.builder().name("Pelada financeira").dayOfWeek("SATURDAY").timeOfDay("19:00").duration(1f)
                .creator(admin).members(new HashSet<>(Set.of(admin, member, other))).admins(new HashSet<>(Set.of(admin))).build());
        // only designated responsáveis manage; the creator starts as one, as PeladaService.createPelada does
        em.flush(); appoint(admin);
    }
    void appoint(User user) { var m = new FinanceManager(); m.setPeladaId(id()); m.setUserId(user.getId()); managerRows.saveAndFlush(m); }
    FinanceRequests.Managers managers(User... users) { return new FinanceRequests.Managers(command(), Arrays.stream(users).map(User::getId).toList()); }
    User user(String name) { return users.save(User.builder().username(name + UUID.randomUUID()).email(UUID.randomUUID() + "@finance.test").password("hash").build()); }
    Long id() { return pelada.getId(); }
    FinanceRequests.Command command() { return command("Correção justificada"); }
    FinanceRequests.Command command(String reason) { return new FinanceRequests.Command(accounts.findByPeladaId(id()).map(FinanceAccount::getRevision).orElse(0L), UUID.randomUUID(), reason); }
    List<FinanceRequests.Component> price() { return List.of(new FinanceRequests.Component("Campo", "40.00"), new FinanceRequests.Component("Juiz", "5.00")); }
    Long draft(Classification kind) {
        return collections.collection(id(), null, new FinanceRequests.Collection(command(), "Arrecadação", kind, "Quatro diárias, sem compensação automática", LocalDate.of(2026,10,30), null, price(), "pix-original", "Recebedor", null), admin.getEmail()).detail().collection().id();
    }
    Long add(Long listId, User player) {
        return collections.charge(id(), listId, null, false, new FinanceRequests.Charge(command(), player.getId(), null, null, price(), "0.00", LocalDate.of(2026,10,30)), admin.getEmail()).detail().charges().getLast().id();
    }
    Long open() { Long l = draft(Classification.DAILY); add(l, member); collections.transition(id(), l, "PUBLISH", command(), admin.getEmail()); return l; }
    Long charge(Long listId) { return reads.detail(id(), listId, admin.getEmail()).charges().getFirst().id(); }
    void receive(Long l, String value) { ledger.receive(id(), l, charge(l), new FinanceRequests.Receipt(command(), value, null, PaymentMethod.PIX), admin.getEmail()); }
    FinanceViews.Charge view(Long listId) { return reads.detail(id(), listId, admin.getEmail()).charges().getFirst(); }
    void fails(Runnable action, HttpStatus status) { assertThatThrownBy(action::run).isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getStatus()).isEqualTo(status)); }
    Long openWithTwo() { Long l = draft(Classification.DAILY); add(l, member); add(l, other); collections.transition(id(), l, "PUBLISH", command(), admin.getEmail()); return l; }
    List<Long> chargeIds(Long l) { return reads.detail(id(), l, admin.getEmail()).charges().stream().map(FinanceViews.Charge::id).toList(); }
    FinanceRequests.ReceiveAll receiveAll(List<Long> ids) { return new FinanceRequests.ReceiveAll(command(), ids, null, PaymentMethod.CASH); }
    @Test void receiveAllSettlesEveryRemainingAmountInOneRevisionAndReplaysSafely() {
        Long l = openWithTwo(); receive(l, "20.00");
        long revision = accounts.findByPeladaId(id()).orElseThrow().getRevision();
        int auditsBefore = audits.findByPeladaId(id()).size();
        var request = receiveAll(chargeIds(l));
        var result = ledger.receiveAll(id(), l, request, admin.getEmail());
        assertThat(result.detail().charges()).allSatisfy(c -> {
            assertThat(c.paymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(c.outstanding()).isEqualTo("0.00");
        });
        assertThat(result.overview().revision()).isEqualTo(revision + 1);
        assertThat(result.overview().balance()).isEqualTo("90.00");
        assertThat(audits.findByPeladaId(id())).hasSize(auditsBefore + 1);
        var replay = ledger.receiveAll(id(), l, request, admin.getEmail());
        assertThat(replay.overview().balance()).isEqualTo("90.00");
        assertThat(audits.findByPeladaId(id())).hasSize(auditsBefore + 1);
    }
    @Test void receiveAllIsAllOrNothing() {
        Long l = openWithTwo(); var ids = chargeIds(l);
        ledger.report(id(), l, ids.getLast(), command(), null, other.getEmail());
        fails(() -> ledger.receiveAll(id(), l, receiveAll(ids), admin.getEmail()), HttpStatus.CONFLICT);
        assertThat(reads.detail(id(), l, admin.getEmail()).collection().totals().received()).isEqualTo("0.00");
        receive(l, "45.00");
        fails(() -> ledger.receiveAll(id(), l, receiveAll(List.of(ids.getFirst())), admin.getEmail()), HttpStatus.CONFLICT);
        fails(() -> ledger.receiveAll(id(), l, receiveAll(List.of(ids.getFirst(), ids.getFirst())), admin.getEmail()), HttpStatus.CONFLICT);
    }
    @Test void receiveAllChecksManagerAndScopesChargesToTheList() {
        Long l = openWithTwo(); Long other = open();
        fails(() -> ledger.receiveAll(id(), l, receiveAll(chargeIds(l)), member.getEmail()), HttpStatus.FORBIDDEN);
        fails(() -> ledger.receiveAll(id(), l, receiveAll(List.of(chargeIds(l).getFirst(), charge(other))), admin.getEmail()), HttpStatus.NOT_FOUND);
        collections.transition(id(), other, "CLOSE", command(), admin.getEmail());
        fails(() -> ledger.receiveAll(id(), other, receiveAll(List.of(charge(other))), admin.getEmail()), HttpStatus.CONFLICT);
    }
    @Test void receiveAllRestValidatesTheBody() throws Exception {
        Long l = openWithTwo();
        String path = "/api/v1/peladas/" + id() + "/finance/lists/" + l + "/receive-all";
        mockMvc.perform(post(path).header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(receiveAll(List.of()))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path).header("Authorization", bearerToken(outsider.getId(), outsider.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(receiveAll(chargeIds(l)))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path).header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(receiveAll(chargeIds(l)))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detail.collection.totals.outstanding").value("0.00"));
    }
    FinanceRequests.BulkAdjust adjust(List<Long> ids, String reason) {
        return new FinanceRequests.BulkAdjust(command(reason), ids, List.of(new FinanceRequests.Component("Juiz", "5.00")), "0.00");
    }
    Long openWithThree() {
        pelada.getMembers().add(outsider); peladas.save(pelada);
        Long l = draft(Classification.DAILY); add(l, member); add(l, other); add(l, outsider);
        collections.transition(id(), l, "PUBLISH", command(), admin.getEmail()); return l;
    }
    @Test void adjustChargesReplacesEveryCompositionInOneRevisionAndReplaysSafely() {
        Long l = openWithThree(); var ids = chargeIds(l);
        long revision = accounts.findByPeladaId(id()).orElseThrow().getRevision();
        int auditsBefore = audits.findByPeladaId(id()).size();
        var request = adjust(ids, "Mensalistas pagam só o juiz");
        var result = collections.adjustCharges(id(), l, request, admin.getEmail());
        assertThat(result.detail().charges()).hasSize(3).allSatisfy(c -> {
            assertThat(c.amountDue()).isEqualTo("5.00");
            assertThat(c.components()).extracting(FinanceViews.Component::name).containsExactly("Juiz");
            assertThat(c.dueDate()).isEqualTo(LocalDate.of(2026, 10, 30));
        });
        assertThat(result.overview().revision()).isEqualTo(revision + 1);
        var entries = audits.findByPeladaId(id());
        assertThat(entries).hasSize(auditsBefore + 1);
        var entry = entries.stream().filter(a -> a.getRequestKey().equals(request.command().requestKey())).findFirst().orElseThrow();
        assertThat(entry.getOperation()).isEqualTo(FinanceTypes.AuditOperation.CHARGE_SAVE);
        assertThat(entry.getTarget()).isEqualTo("LIST:" + l);
        collections.adjustCharges(id(), l, request, admin.getEmail());
        assertThat(audits.findByPeladaId(id())).hasSize(auditsBefore + 1);
    }
    @Test void adjustChargesIsAllOrNothing() {
        Long l = openWithThree(); var ids = chargeIds(l);
        ledger.report(id(), l, ids.get(1), command(), null, other.getEmail());
        fails(() -> collections.adjustCharges(id(), l, adjust(ids, "Juiz"), admin.getEmail()), HttpStatus.CONFLICT);
        collections.cancelCharge(id(), l, ids.get(2), command(), admin.getEmail());
        fails(() -> collections.adjustCharges(id(), l, adjust(List.of(ids.getFirst(), ids.get(2)), "Juiz"), admin.getEmail()), HttpStatus.CONFLICT);
        ledger.receive(id(), l, ids.getFirst(), new FinanceRequests.Receipt(command(), "20.00", null, PaymentMethod.PIX), admin.getEmail());
        assertThatThrownBy(() -> collections.adjustCharges(id(), l, adjust(List.of(ids.getFirst()), "Juiz"), admin.getEmail()))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains(member.getUsername()).contains("R$ 20,00");
                });
        assertThat(reads.detail(id(), l, admin.getEmail()).charges()).filteredOn(c -> !c.canceled()).allSatisfy(c -> assertThat(c.amountDue()).isEqualTo("45.00"));
    }
    @Test void adjustChargesChecksManagerReasonAndScope() {
        Long l = openWithThree(); Long other = open();
        fails(() -> collections.adjustCharges(id(), l, adjust(chargeIds(l), "Juiz"), member.getEmail()), HttpStatus.FORBIDDEN);
        fails(() -> collections.adjustCharges(id(), l, adjust(List.of(chargeIds(l).getFirst(), charge(other)), "Juiz"), admin.getEmail()), HttpStatus.NOT_FOUND);
        fails(() -> collections.adjustCharges(id(), l, adjust(chargeIds(l), null), admin.getEmail()), HttpStatus.BAD_REQUEST);
        fails(() -> collections.adjustCharges(id(), l, adjust(List.of(charge(l), charge(l)), "Juiz"), admin.getEmail()), HttpStatus.BAD_REQUEST);
        Long draft = draft(Classification.DAILY); add(draft, member);
        assertThat(collections.adjustCharges(id(), draft, adjust(chargeIds(draft), null), admin.getEmail()).detail().charges().getFirst().amountDue()).isEqualTo("5.00");
    }
    @Test void adjustChargesRestValidatesTheBody() throws Exception {
        Long l = openWithThree();
        String path = "/api/v1/peladas/" + id() + "/finance/lists/" + l + "/charges/adjust";
        String token = bearerToken(admin.getId(), admin.getEmail());
        var tooMany = java.util.stream.LongStream.rangeClosed(1, 201).boxed().toList();
        for (var body : List.of(adjust(List.of(), "Juiz"), adjust(tooMany, "Juiz"))) {
            mockMvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
        var invalid = new FinanceRequests.BulkAdjust(command("Juiz"), chargeIds(l), List.of(new FinanceRequests.Component("Juiz", "5")), "0.00");
        mockMvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path).header("Authorization", bearerToken(member.getId(), member.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(adjust(chargeIds(l), "Juiz"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(adjust(chargeIds(l), "Juiz"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detail.collection.totals.due").value("15.00"));
    }
    @Test void guidedCreationPublishesPeopleAndExactTotalsInOneRevisionAndReplaysSafely() {
        var request = new FinanceRequests.CreateCollection(
                new FinanceRequests.Collection(command(), "Diária de sábado", Classification.DAILY, "", null, null, price(), "pix", "Gestor", null),
                List.of(member.getId(), other.getId()), List.of("Convidado", "Convidado"), true);
        var result = collections.createCollection(id(), request, admin.getEmail());
        assertThat(result.overview().revision()).isEqualTo(1);
        assertThat(result.detail().collection().status()).isEqualTo(ListStatus.OPEN);
        assertThat(result.detail().charges()).hasSize(4);
        assertThat(result.detail().charges().stream().map(FinanceViews.Charge::personId)).doesNotHaveDuplicates();
        assertThat(result.detail().collection().totals().due()).isEqualTo("180.00");
        assertThat(result.overview().balance()).isEqualTo("0.00");
        var replay = collections.createCollection(id(), request, admin.getEmail());
        assertThat(replay.detail().collection().id()).isEqualTo(result.detail().collection().id());
        assertThat(lists.findByPeladaId(id())).hasSize(1);
        assertThat(audits.findByPeladaId(id())).hasSize(1);
        assertThat(reads.detail(id(), result.detail().collection().id(), member.getEmail()).charges()).hasSize(1);
    }
    // `size` new members, half of them with a financial identity from an earlier list
    List<Long> membersHalfKnown(int size) {
        Long earlier = draft(Classification.DAILY);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            var u = user("Jogador"); pelada.getMembers().add(u); ids.add(u.getId());
            if (i % 2 == 0) add(earlier, u);
        }
        return ids;
    }
    List<String> guests(int size) { return java.util.stream.IntStream.range(0, size).mapToObj(i -> "Convidado " + i).toList(); }
    long participantReads(int size) {
        var ids = membersHalfKnown(size); Long l = draft(Classification.DAILY);
        return queryCounter.reads(() -> collections.participants(id(), l, new FinanceRequests.Participants(command(), ids, guests(size)), admin.getEmail()));
    }
    long guidedCreationReads(int size) {
        var ids = membersHalfKnown(size);
        var request = new FinanceRequests.CreateCollection(new FinanceRequests.Collection(command(), "Mensalidade", Classification.MONTHLY, "", null, null, price(), null, null, null),
                ids, guests(size), true);
        return queryCounter.reads(() -> collections.createCollection(id(), request, admin.getEmail()));
    }
    @Test void addingParticipantsReadsAConstantNumberOfQueries() {
        participantReads(2); guidedCreationReads(2); // warm-up: one-off context loads (account, caller) don't count
        assertThat(participantReads(20)).isEqualTo(participantReads(2));
        assertThat(guidedCreationReads(20)).isEqualTo(guidedCreationReads(2));
    }
    @Test void aParticipantConflictNamesThePersonAndWritesNothing() {
        Long l = draft(Classification.DAILY); add(l, member);
        long revision = accounts.findByPeladaId(id()).orElseThrow().getRevision();
        int peopleBefore = people.findByPeladaId(id()).size(), auditsBefore = audits.findByPeladaId(id()).size();
        assertThatThrownBy(() -> collections.participants(id(), l,
                new FinanceRequests.Participants(command(), List.of(other.getId(), member.getId()), List.of("Convidado")), admin.getEmail()))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo(member.getUsername() + " já tem cobrança nesta lista");
                });
        assertThat(people.findByPeladaId(id())).hasSize(peopleBefore);
        assertThat(charges.findByListIdOrderById(l)).hasSize(1);
        assertThat(accounts.findByPeladaId(id()).orElseThrow().getRevision()).isEqualTo(revision);
        assertThat(audits.findByPeladaId(id())).hasSize(auditsBefore);
    }
    @Test void guidedCreationAllowsAnEmptyDraftButRequiresManagerPermission() {
        var request = new FinanceRequests.CreateCollection(
                new FinanceRequests.Collection(command(), "Rascunho", Classification.OTHER, "", null, null, price(), null, null, null),
                List.of(), List.of(), false);
        fails(() -> collections.createCollection(id(), request, member.getEmail()), HttpStatus.FORBIDDEN);
        var result = collections.createCollection(id(), request, admin.getEmail());
        assertThat(result.detail().collection().status()).isEqualTo(ListStatus.DRAFT);
        assertThat(result.detail().charges()).isEmpty();
    }
    @Test void guidedCreationRestValidatesNestedAmountsAndParticipants() throws Exception {
        var request = new FinanceRequests.CreateCollection(
                new FinanceRequests.Collection(command(), "Diária", Classification.DAILY, "", null, null, price(), null, null, null),
                List.of(member.getId()), List.of(), true);
        String path = "/api/v1/peladas/" + id() + "/finance/lists/create";
        String json = objectMapper.writeValueAsString(request);
        mockMvc.perform(post(path).header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.replace("40.00", "40.001")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path).header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(json.replace("\"guestNames\":[]", "\"guestNames\":[\" \"]")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(path).header("Authorization", bearerToken(admin.getId(), admin.getEmail()))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detail.collection.status").value("OPEN"))
                .andExpect(jsonPath("$.detail.charges[0].amountDue").value("45.00"));
    }
    @Test void offlinePartialDiscountRefundAndReversalReconcileDebtCashAndAudit() {
        Long l = open(); receive(l, "20.00");
        assertThat(view(l).paymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        collections.charge(id(), l, charge(l), false, new FinanceRequests.Charge(command(), null, null, null, price(), "-5.00", null), admin.getEmail());
        receive(l, "20.00");
        assertThat(view(l).paymentStatus()).isEqualTo(PaymentStatus.PAID);
        var refund = ledger.refund(id(), l, charge(l), new FinanceRequests.Refund(command(), "10.00", null, PaymentMethod.CASH, true), admin.getEmail());
        assertThat(refund.overview().balance()).isEqualTo("30.00"); assertThat(view(l).amountDue()).isEqualTo("30.00");
        Long refundId = reads.ledger(id(), admin.getEmail(), 0, 20, null, null).items().getFirst().id();
        ledger.reverse(id(), refundId, command(), admin.getEmail());
        assertThat(view(l).amountDue()).isEqualTo("40.00"); assertThat(view(l).received()).isEqualTo("40.00");
        assertThat(reads.overview(id(), admin.getEmail(), 0, 50).balance()).isEqualTo("40.00");
        assertThat(reads.audit(id(), admin.getEmail(), 0, 50, null, null, null).items()).allSatisfy(a -> assertThat(a.actorId()).isEqualTo(admin.getId()));
    }
    @Test void pendingIsNotCashBlocksChangesAndConfirmationIsIdempotent() {
        Long l = open(); var report = command(); ledger.report(id(), l, charge(l), report, null, member.getEmail());
        assertThat(reads.overview(id(), admin.getEmail(), 0, 50).balance()).isEqualTo("0.00");
        var notice = view(l).notices().getFirst();
        fails(() -> receive(l, "1.00"), HttpStatus.CONFLICT);
        fails(() -> collections.transition(id(), l, "CLOSE", command(), admin.getEmail()), HttpStatus.CONFLICT);
        fails(() -> collections.charge(id(), l, charge(l), false, new FinanceRequests.Charge(command(), null, null, null, price(), "-1.00", null), admin.getEmail()), HttpStatus.CONFLICT);
        var confirmation = new FinanceRequests.Confirmation(command(), LocalDate.of(2026,10,30), PaymentMethod.PIX);
        ledger.confirm(id(), l, charge(l), notice.id(), confirmation, admin.getEmail());
        ledger.confirm(id(), l, charge(l), notice.id(), confirmation, admin.getEmail());
        assertThat(movements.findByChargeIdIn(List.of(charge(l)))).hasSize(1);
        assertThat(view(l).received()).isEqualTo("45.00");
        assertThat(reads.overview(id(), admin.getEmail(), 0, 50).balance()).isEqualTo("45.00");
    }
    @Test void rejectionRequiresReasonAndAnotherNoticeCanBeSentWithoutChangingAttendanceOrCategory() {
        Long l = open(); ledger.report(id(), l, charge(l), command(), null, member.getEmail());
        Long noticeId = view(l).notices().getFirst().id();
        fails(() -> ledger.reject(id(), l, charge(l), noticeId, command(""), admin.getEmail()), HttpStatus.BAD_REQUEST);
        ledger.reject(id(), l, charge(l), noticeId, command("Valor não localizado"), admin.getEmail());
        ledger.report(id(), l, charge(l), command(), null, member.getEmail());
        assertThat(view(l).notices()).hasSize(2);
        assertThat(view(l).notices().getLast().reason()).isEqualTo("Valor não localizado");
    }
    @Test void individualAndTransparentViewsNeverLeakNoticesReceiptsAuditOrDrafts() {
        Long l = open(); add(l, other);
        ledger.report(id(), l, charge(l), command(), null, member.getEmail());
        Long hidden = draft(Classification.OTHER);
        assertThat(reads.overview(id(), member.getEmail(), 0, 50).balance()).isNull();
        assertThat(reads.detail(id(), l, member.getEmail()).charges()).hasSize(1);
        fails(() -> reads.detail(id(), hidden, member.getEmail()), HttpStatus.NOT_FOUND);
        fails(() -> reads.ledger(id(), member.getEmail(), 0, 20, null, null), HttpStatus.FORBIDDEN);
        fails(() -> reads.audit(id(), member.getEmail(), 0, 20, null, null, null), HttpStatus.FORBIDDEN);
        collections.settings(id(), new FinanceRequests.Settings(command(), Visibility.TRANSPARENT, "novo", "Novo", null), admin.getEmail());
        var visible = reads.detail(id(), l, other.getEmail()); assertThat(visible.charges()).hasSize(2);
        assertThat(visible.charges().getFirst().notices()).isEmpty();
        assertThat(visible.collection().pixKey()).isEqualTo("pix-original");
        assertThat(reads.overview(id(), other.getEmail(), 0, 50).lists()).noneMatch(row -> row.id().equals(hidden));
    }
    @Test void adminsWithoutDesignationOnlyAppointAndResponsaveisAppointToo() {
        pelada.getAdmins().add(other); peladas.saveAndFlush(pelada);
        var adminView = reads.overview(id(), other.getEmail(), 0, 50).permissions();
        assertThat(adminView.manage()).isFalse(); assertThat(adminView.appointManagers()).isTrue(); assertThat(adminView.viewCash()).isFalse();
        fails(this::draftByOther, HttpStatus.FORBIDDEN);
        collections.managers(id(), managers(admin, member), other.getEmail());
        var delegate = reads.overview(id(), member.getEmail(), 0, 50).permissions();
        assertThat(delegate.manage()).isTrue(); assertThat(delegate.appointManagers()).isTrue();
        // a responsável who isn't admin appoints too, admins included
        collections.managers(id(), managers(member, other), member.getEmail());
        assertThat(reads.overview(id(), other.getEmail(), 0, 50).permissions().manage()).isTrue();
        assertThat(reads.overview(id(), admin.getEmail(), 0, 50).permissions().manage()).isFalse();
        assertThat(reads.overview(id(), admin.getEmail(), 0, 50).settings().managerIds()).containsExactly(member.getId(), other.getId());
        // stepping down answers with the caller's new permissions
        var stepDown = collections.managers(id(), managers(other), member.getEmail()).overview().permissions();
        assertThat(stepDown.manage()).isFalse(); assertThat(stepDown.appointManagers()).isFalse();
        fails(() -> collections.managers(id(), managers(), other.getEmail()), HttpStatus.CONFLICT);
        assertThat(managerRows.findByPeladaId(id())).extracting(FinanceManager::getUserId).containsExactly(other.getId());
    }
    void draftByOther() {
        collections.collection(id(), null, new FinanceRequests.Collection(command(), "Arrecadação", Classification.DAILY, null, null, null, price(), null, null, null), other.getEmail());
    }
    @Test void ordinaryMembersCannotAppointAndRemovalRevokesAccessButKeepsIdentity() {
        Long l = open(); collections.managers(id(), managers(admin, member), admin.getEmail());
        assertThat(reads.overview(id(), other.getEmail(), 0, 50).permissions().appointManagers()).isFalse();
        fails(() -> collections.managers(id(), managers(admin, other), other.getEmail()), HttpStatus.FORBIDDEN);
        Long identity = view(l).personId();
        peladaService.removePlayer(id(), member.getId(), admin.getEmail());
        fails(() -> reads.overview(id(), member.getEmail(), 0, 50), HttpStatus.FORBIDDEN);
        assertThat(view(l).personId()).isEqualTo(identity);
        pelada.getMembers().add(member); peladas.saveAndFlush(pelada);
        assertThat(reads.overview(id(), member.getEmail(), 0, 50).permissions().manage()).isFalse();
        assertThat(reads.detail(id(), l, member.getEmail()).charges().getFirst().personId()).isEqualTo(identity);
    }
    @Test void removingEveryResponsavelIsRefusedAndChangesNothing() {
        collections.managers(id(), managers(member), admin.getEmail());
        assertThatThrownBy(() -> peladaService.removePlayer(id(), member.getId(), admin.getEmail()))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo(member.getUsername() + " é o único responsável financeiro. Designe outro antes de removê-lo.");
                });
        collections.managers(id(), managers(member, other), admin.getEmail());
        assertThatThrownBy(() -> peladaService.removePlayers(id(), List.of(member.getId(), other.getId()), admin.getEmail()))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(peladas.findById(id()).orElseThrow().getMembers()).contains(member, other);
        assertThat(managerRows.findByPeladaId(id())).extracting(FinanceManager::getUserId).containsExactlyInAnyOrder(member.getId(), other.getId());
        // one of them may leave while the other stays
        peladaService.removePlayer(id(), member.getId(), admin.getEmail());
        assertThat(managerRows.findByPeladaId(id())).extracting(FinanceManager::getUserId).containsExactly(other.getId());
    }
    @Test void theCreatorOfANewPeladaIsItsResponsavel() {
        var created = peladaService.createPelada(new CreatePeladaRequestDTO("Nova", "FRIDAY", "20:00", 1f, null, null, false, 2, 5, null, 0), member.getEmail());
        var permissions = reads.overview(created.id(), member.getEmail(), 0, 50).permissions();
        assertThat(permissions.manage()).isTrue(); assertThat(permissions.appointManagers()).isTrue();
    }
    @Test void childrenAreScopedAndGuestsWithEqualNamesRemainDifferent() {
        Long l = open(); Long another = draft(Classification.OTHER);
        fails(() -> ledger.receive(id(), another, charge(l), new FinanceRequests.Receipt(command(), "1.00", null, PaymentMethod.CASH), admin.getEmail()), HttpStatus.CONFLICT);
        fails(() -> ledger.report(id(), l, charge(l), command(), null, other.getEmail()), HttpStatus.FORBIDDEN);
        fails(() -> reads.overview(id(), outsider.getEmail(), 0, 20), HttpStatus.FORBIDDEN);
        fails(() -> collections.collection(id(), null, new FinanceRequests.Collection(command(), "Negada", Classification.OTHER, "", null, null, price(), null, null, null), member.getEmail()), HttpStatus.FORBIDDEN);
        var p = new FinanceRequests.Participants(command(), List.of(), List.of("Convidado", "Convidado"));
        var result = collections.participants(id(), another, p, admin.getEmail());
        assertThat(result.detail().charges()).hasSize(2); assertThat(result.detail().charges().get(0).personId()).isNotEqualTo(result.detail().charges().get(1).personId());
        var foreign = peladas.save(Pelada.builder().name("Outra").dayOfWeek("SATURDAY").timeOfDay("19:00").duration(1f).creator(admin).members(new HashSet<>(Set.of(admin))).admins(new HashSet<>(Set.of(admin))).build());
        fails(() -> reads.detail(foreign.getId(), l, admin.getEmail()), HttpStatus.NOT_FOUND);
    }
    @Test void listsArePaginatedPerScopeWithTotalsAndMembersOnlyCountTheirOwnLists() {
        Long first = open(); Long second = open(); Long third = open();
        collections.transition(id(), third, "CLOSE", command(), admin.getEmail());
        Long hidden = draft(Classification.OTHER);
        var active = reads.overview(id(), admin.getEmail(), ListScope.ACTIVE, 0, 2);
        assertThat(active.lists()).extracting(FinanceViews.Collection::id).containsExactly(hidden, second);
        assertThat(active.total()).isEqualTo(3); assertThat(active.hasMore()).isTrue();
        var next = reads.overview(id(), admin.getEmail(), ListScope.ACTIVE, 1, 2);
        assertThat(next.lists()).extracting(FinanceViews.Collection::id).containsExactly(first);
        assertThat(next.hasMore()).isFalse();
        var archived = reads.overview(id(), admin.getEmail(), ListScope.ARCHIVED, 0, 2);
        assertThat(archived.lists()).extracting(FinanceViews.Collection::id).containsExactly(third);
        assertThat(archived.total()).isEqualTo(1);
        // the member has a charge in the three published lists and never sees the draft, in the list or in the total
        var mine = reads.overview(id(), member.getEmail(), ListScope.ACTIVE, 0, 50);
        assertThat(mine.lists()).extracting(FinanceViews.Collection::id).containsExactly(second, first);
        assertThat(mine.total()).isEqualTo(2);
        assertThat(reads.overview(id(), other.getEmail(), ListScope.ACTIVE, 0, 50).total()).isZero();
    }
    @Test void ledgerAndAuditPagesReportTheirTotal() {
        Long l = open(); receive(l, "10.00"); receive(l, "5.00"); receive(l, "1.00");
        var page = reads.ledger(id(), admin.getEmail(), 0, 2, null, null);
        assertThat(page.items()).hasSize(2); assertThat(page.hasMore()).isTrue(); assertThat(page.total()).isEqualTo(3);
        assertThat(reads.ledger(id(), admin.getEmail(), 1, 2, null, null).items()).hasSize(1);
        var audit = reads.audit(id(), admin.getEmail(), 0, 50, null, null, null);
        assertThat(audit.total()).isEqualTo(audit.items().size());
    }
    // Reads per transition (publish, close, reopen, cancel) of a list with `size` participants
    List<Long> transitionReads(int size) {
        Long l = draft(Classification.DAILY);
        for (int i = 0; i < size; i++) { var u = user("Jogador"); pelada.getMembers().add(u); add(l, u); }
        long publish = queryCounter.reads(() -> collections.transition(id(), l, "PUBLISH", command(), admin.getEmail()));
        // A receipt registered by mistake and reversed: cancel goes through paid() and hasValidReceipts()
        receive(l, "10.00");
        Long receipt = movements.findByChargeIdIn(List.of(charge(l))).getFirst().getId();
        ledger.reverse(id(), receipt, command(), admin.getEmail());
        long close = queryCounter.reads(() -> collections.transition(id(), l, "CLOSE", command(), admin.getEmail()));
        long reopen = queryCounter.reads(() -> collections.transition(id(), l, "REOPEN", command(), admin.getEmail()));
        long cancel = queryCounter.reads(() -> collections.transition(id(), l, "CANCEL", command(), admin.getEmail()));
        assertThat(lists.findById(l).orElseThrow().getStatus()).isEqualTo(ListStatus.CANCELED);
        return List.of(publish, close, reopen, cancel);
    }
    @Test void transitionsReadAConstantNumberOfQueries() {
        transitionReads(2); // warm-up: one-off context loads (account, caller) don't count
        assertThat(transitionReads(20)).isEqualTo(transitionReads(2));
    }
    @Test void aPendingNoticeOnAnyChargeBlocksClosing() {
        Long l = draft(Classification.DAILY); add(l, admin); add(l, member); add(l, other);
        collections.transition(id(), l, "PUBLISH", command(), admin.getEmail());
        ledger.report(id(), l, chargeIds(l).getLast(), command(), null, other.getEmail());
        assertThatThrownBy(() -> collections.transition(id(), l, "CLOSE", command(), admin.getEmail()))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("Resolva todos os avisos pendentes antes de encerrar");
                });
        assertThat(lists.findById(l).orElseThrow().getStatus()).isEqualTo(ListStatus.OPEN);
    }
    @Test void closeReopenDuplicateAndReplacementPreserveAllocations() {
        Long l = open(); receive(l, "20.00"); collections.transition(id(), l, "CLOSE", command(), admin.getEmail());
        fails(() -> receive(l, "5.00"), HttpStatus.CONFLICT);
        collections.transition(id(), l, "REOPEN", command(), admin.getEmail());
        var replaced = collections.charge(id(), l, charge(l), true, new FinanceRequests.Charge(command(), other.getId(), null, null, price(), "0.00", null), admin.getEmail()).detail();
        assertThat(replaced.charges().getFirst().canceled()).isTrue(); assertThat(replaced.charges().getFirst().received()).isEqualTo("20.00");
        assertThat(replaced.charges().getLast().received()).isEqualTo("0.00");
        var copy = collections.duplicate(id(), l, command(), admin.getEmail()).detail();
        assertThat(copy.collection().status()).isEqualTo(ListStatus.DRAFT); assertThat(copy.charges()).hasSize(1);
        assertThat(copy.charges().getFirst().notices()).isEmpty(); assertThat(copy.charges().getFirst().received()).isEqualTo("0.00");
    }
    @Test void dailyDeletionDetachesAndPublishedHistoryBlocksPeladaDeletion() {
        var daily = dailies.save(Daily.builder().pelada(pelada).dailyDate(LocalDate.of(2026,10,31)).dailyTime("19:00").build());
        Long l = collections.collection(id(), null, new FinanceRequests.Collection(command(), "Diária", Classification.DAILY, "", null, daily.getId(), price(), null, null, null), admin.getEmail()).detail().collection().id();
        add(l, member); collections.transition(id(), l, "PUBLISH", command(), admin.getEmail());
        dailyService.deleteDaily(daily.getId(), admin.getEmail()); em.flush();
        assertThat(reads.detail(id(), l, admin.getEmail()).collection().dailyId()).isNull(); assertThat(reads.detail(id(), l, admin.getEmail()).collection().dailyLabel()).contains("2026-10-31");
        collections.transition(id(), l, "CANCEL", command(), admin.getEmail());
        fails(() -> peladaService.deletePelada(id(), admin.getEmail()), HttpStatus.CONFLICT);
    }
    @Test void draftOnlyPeladaCanStillBeDeleted() {
        Long l = draft(Classification.MONTHLY); add(l, member);
        peladaService.deletePelada(id(), admin.getEmail()); em.flush(); assertThat(peladas.existsById(id())).isFalse();
    }
    @Test void correctedOpeningBalanceAndCanceledListsNeverEraseCash() {
        ledger.cash(id(), new FinanceRequests.Cash(command(), MovementKind.OPENING, "-10.00", "Saldo inicial", null, null, PaymentMethod.OTHER, null, null), admin.getEmail());
        Long opening = reads.ledger(id(), admin.getEmail(), 0, 20, null, null).items().getFirst().id();
        ledger.reverse(id(), opening, command(), admin.getEmail());
        ledger.cash(id(), new FinanceRequests.Cash(command(), MovementKind.OPENING, "15.00", "Saldo corrigido", null, null, PaymentMethod.OTHER, null, null), admin.getEmail());
        Long l = draft(Classification.OTHER);
        ledger.cash(id(), new FinanceRequests.Cash(command(), MovementKind.EXPENSE, "20.00", "Reserva", "Campo", null, PaymentMethod.PIX, l, null), admin.getEmail());
        collections.transition(id(), l, "CANCEL", command(), admin.getEmail());
        assertThat(reads.overview(id(), admin.getEmail(), 0, 50).balance()).isEqualTo("-5.00");
        fails(() -> peladaService.deletePelada(id(), admin.getEmail()), HttpStatus.CONFLICT);
    }
    @Test void staleRevisionAndTooLargeDiscountLeaveCurrentObligationAndAuditIntact() {
        Long l = open(); var stale = command(); receive(l, "20.00"); long count = audits.findByPeladaId(id()).size();
        fails(() -> collections.transition(id(), l, "CLOSE", stale, admin.getEmail()), HttpStatus.CONFLICT);
        fails(() -> collections.charge(id(), l, charge(l), false, new FinanceRequests.Charge(command(), null, null, null, price(), "-30.00", null), admin.getEmail()), HttpStatus.CONFLICT);
        // The managed entity was touched before validation; a committed transaction must roll back all changes.
        // Real committed rollback is additionally tested by FinanceConcurrencyTest.
        assertThat(audits.findByPeladaId(id())).hasSize((int) count);
    }
    @Test void parentScopedNoticesAndAuthenticatedReceiptHeaders() throws Exception {
        Long l = open(); Long otherCharge = add(l, other);
        ledger.report(id(), l, charge(l), command(), image(), member.getEmail());
        Long notice = view(l).notices().getFirst().id();
        fails(() -> ledger.confirm(id(), l, otherCharge, notice, new FinanceRequests.Confirmation(command(), null, PaymentMethod.PIX), admin.getEmail()), HttpStatus.NOT_FOUND);
        String path = "/api/v1/peladas/" + id() + "/finance/notices/" + notice + "/receipt";
        mockMvc.perform(get(path).header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(content().contentType("image/png"));
        mockMvc.perform(get(path).header("Authorization", bearerToken(other.getId(), other.getEmail()))).andExpect(status().isNotFound());
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    @Test void qrSnapshotsSurviveDefaultReplacementAndRemovalWithScopedVisibility() throws Exception {
        Long firstQr = attachments.qr(id(), null, command(), image(), admin.getEmail()).overview().settings().qrId();
        Long l = draft(Classification.DAILY); add(l, member); collections.transition(id(), l, "PUBLISH", command(), admin.getEmail());
        Long nextQr = attachments.qr(id(), null, command(), image(), admin.getEmail()).overview().settings().qrId();
        assertThat(nextQr).isNotEqualTo(firstQr);
        collections.settings(id(), new FinanceRequests.Settings(command(), Visibility.INDIVIDUAL, "nova-chave", "Gestor", null), admin.getEmail());
        assertThat(reads.overview(id(), admin.getEmail(), 0, 50).settings().qrId()).isNull();
        assertThat(reads.detail(id(), l, member.getEmail()).collection().qrId()).isEqualTo(firstQr);
        assertThat(attachments.qr(id(), firstQr, member.getEmail()).bytes()).isNotEmpty();
        fails(() -> attachments.qr(id(), firstQr, other.getEmail()), HttpStatus.NOT_FOUND);
    }
    @Test void corruptAndMisdeclaredImagesAreRejectedWithoutStorage() throws Exception {
        assertThatThrownBy(() -> files.verify(new MockMultipartFile("file", "bad.png", "image/png", new byte[]{1,2,3}))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> files.verify(new MockMultipartFile("file", "fake.jpg", "image/jpeg", image().getBytes()))).isInstanceOf(AppException.class);
    }
    @Test void cleanupRetriesFailedFilesAndRecoversCrashOrphans() throws Exception {
        Long l = open(); ledger.report(id(), l, charge(l), command(), image(), member.getEmail());
        var notice = notices.findById(view(l).notices().getFirst().id()).orElseThrow();
        Path path = files.directory(false).resolve(notice.getReceiptKey()); Files.delete(path); Files.createDirectory(path);
        Path blocker = path.resolve("block"); Files.writeString(blocker, "test"); clock.now = NOW.plus(Duration.ofDays(7));
        cleanup.cleanup(); assertThat(notice.getReceiptDeletedAt()).isNull();
        Files.delete(blocker); Files.delete(path);
        Path orphan = files.directory(false).resolve(UUID.randomUUID() + ".png"); Files.writeString(orphan, "crash orphan");
        Files.setLastModifiedTime(orphan, java.nio.file.attribute.FileTime.from(NOW.minus(Duration.ofHours(2))));
        cleanup.cleanup(); assertThat(notice.getReceiptDeletedAt()).isEqualTo(clock.now); assertThat(Files.exists(orphan)).isFalse();
    }
    MockMultipartFile image() throws Exception {
        var bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB), "png", bytes);
        return new MockMultipartFile("file", "comprovante.png", "image/png", bytes.toByteArray());
    }
    @Test void receiptAccessExpiresExactlyAtSevenAndThirtyDaysEvenWhenPendingAndCleanupRecovers() throws Exception {
        for (var kind : Classification.values()) {
            clock.now = NOW; Long l = draft(kind); add(l, member); collections.transition(id(), l, "PUBLISH", command(), admin.getEmail());
            ledger.report(id(), l, charge(l), command(), image(), member.getEmail());
            var notice = notices.findById(view(l).notices().getFirst().id()).orElseThrow();
            int days = kind == Classification.DAILY ? 7 : 30; Instant edge = NOW.plus(Duration.ofDays(days));
            clock.now = edge.minusNanos(1); assertThat(attachments.receipt(id(), notice.getId(), member.getEmail()).bytes()).isNotEmpty();
            fails(() -> attachments.receipt(id(), notice.getId(), other.getEmail()), HttpStatus.NOT_FOUND);
            clock.now = edge; fails(() -> attachments.receipt(id(), notice.getId(), admin.getEmail()), HttpStatus.GONE);
            cleanup.cleanup(); assertThat(Files.exists(files.directory(false).resolve(notice.getReceiptKey()))).isFalse();
            assertThat(notice.getReceiptDeletedAt()).isEqualTo(edge); assertThat(notice.getStatus()).isEqualTo(NoticeStatus.PENDING);
        }
    }
    @Test void restRequiresDecimalStringsAndRejectsExcessPrecisionAndForeignIds() throws Exception {
        Long l = open(); String path = "/api/v1/peladas/" + id() + "/finance/lists/" + l + "/charges/" + charge(l) + "/receive";
        String prefix = "{\"command\":" + objectMapper.writeValueAsString(command()) + ",\"method\":\"PIX\",\"effectiveDate\":null,\"amount\":";
        for (String amount : List.of("45.00", "\"45.001\"", "\"NaN\"", "\"-1.00\"")) {
            mockMvc.perform(post(path).header("Authorization", bearerToken(admin.getId(), admin.getEmail())).contentType(MediaType.APPLICATION_JSON).content(prefix + amount + "}"))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/v1/peladas/" + id() + "/finance").header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").isEmpty()).andExpect(jsonPath("$.lists[0].totals.due").value("45.00"));
    }

    // History filters (SPEC §4/§5): inclusive days in the pelada's zone, filtered totals, compatible without params
    List<Long> auditIds(LocalDate from, LocalDate to, Set<AuditOperation> operations) {
        var page = reads.audit(id(), admin.getEmail(), 0, 50, from, to, operations);
        assertThat(page.total()).isEqualTo(page.items().size());
        return page.items().stream().map(FinanceViews.Audit::id).toList();
    }
    @Test void auditFiltersByDayInThePeladaZone() {
        LocalDate day30 = LocalDate.of(2026, 10, 30), day31 = LocalDate.of(2026, 10, 31);
        clock.now = Instant.parse("2026-10-31T02:59:59Z"); draft(Classification.DAILY);   // 30/10 23:59:59 in São Paulo
        Long late = auditIds(null, null, null).getFirst();
        clock.now = Instant.parse("2026-10-31T03:00:00Z"); draft(Classification.DAILY);   // 31/10 00:00 in São Paulo
        Long midnight = auditIds(null, null, null).getFirst();
        assertThat(auditIds(day30, day30, null)).containsExactly(late);
        assertThat(auditIds(day31, day31, null)).containsExactly(midnight);
        assertThat(auditIds(day31, null, null)).containsExactly(midnight);
        assertThat(auditIds(null, day30, null)).containsExactly(late);
        pelada.setTimeZone("Asia/Tokyo"); peladas.saveAndFlush(pelada);                     // both are 31/10 in Tokyo
        assertThat(auditIds(day30, day30, null)).isEmpty();
        assertThat(auditIds(day31, day31, null)).containsExactly(midnight, late);
    }
    @Test void auditFiltersByOperationAndStaysScopedToThePelada() {
        open();
        assertThat(audits.findByPeladaId(id())).extracting(FinanceAudit::getOperation)
                .containsExactlyInAnyOrder(AuditOperation.LIST_SAVE, AuditOperation.CHARGE_SAVE, AuditOperation.PUBLISH);
        assertThat(reads.audit(id(), admin.getEmail(), 0, 50, null, null, Set.of(AuditOperation.PUBLISH)).items())
                .extracting(FinanceViews.Audit::operation).containsExactly(AuditOperation.PUBLISH);
        assertThat(reads.audit(id(), admin.getEmail(), 0, 50, null, null, EnumSet.of(AuditOperation.LIST_SAVE, AuditOperation.PUBLISH)).total()).isEqualTo(2);
        assertThat(auditIds(null, null, Set.of())).hasSize(3);
        assertThat(reads.audit(id(), admin.getEmail(), 0, 50, null, null, Set.of(AuditOperation.RECEIVE)).total()).isZero();
        Pelada otherPelada = peladas.save(Pelada.builder().name("Outra").dayOfWeek("SUNDAY").timeOfDay("10:00").duration(1f)
                .creator(outsider).members(new HashSet<>(Set.of(outsider))).admins(new HashSet<>(Set.of(outsider))).build());
        em.flush(); var outsiderManager = new FinanceManager(); outsiderManager.setPeladaId(otherPelada.getId()); outsiderManager.setUserId(outsider.getId()); managerRows.saveAndFlush(outsiderManager);
        assertThat(reads.audit(otherPelada.getId(), outsider.getEmail(), 0, 50, null, null, null).total()).isZero();
        assertThat(reads.ledger(otherPelada.getId(), outsider.getEmail(), 0, 50, null, null).total()).isZero();
        fails(() -> reads.audit(id(), member.getEmail(), 0, 20, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), Set.of(AuditOperation.PUBLISH)), HttpStatus.FORBIDDEN);
    }
    @Test void ledgerFiltersByEffectiveDateWithoutChangingTheBalance() {
        Long l = open(); Long c = charge(l);
        for (int day : new int[]{1, 15, 31})
            ledger.receive(id(), l, c, new FinanceRequests.Receipt(command(), "5.00", LocalDate.of(2026, 10, day), PaymentMethod.PIX), admin.getEmail());
        assertThat(reads.ledger(id(), admin.getEmail(), 0, 20, null, null).total()).isEqualTo(3);
        var page = reads.ledger(id(), admin.getEmail(), 0, 20, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15));
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(FinanceViews.Movement::effectiveDate)
                .containsExactly(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 1));
        assertThat(reads.ledger(id(), admin.getEmail(), 0, 20, LocalDate.of(2026, 10, 31), null).items()).hasSize(1);
        assertThat(reads.ledger(id(), admin.getEmail(), 0, 20, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 14)).total()).isZero();
        assertThat(reads.overview(id(), admin.getEmail(), ListScope.ACTIVE, 0, 50).balance()).isEqualTo("15.00");
    }
    @Test void historyFiltersRejectAnInvertedPeriod() {
        LocalDate from = LocalDate.of(2026, 10, 31), to = LocalDate.of(2026, 10, 1);
        fails(() -> reads.audit(id(), admin.getEmail(), 0, 20, from, to, null), HttpStatus.BAD_REQUEST);
        fails(() -> reads.ledger(id(), admin.getEmail(), 0, 20, from, to), HttpStatus.BAD_REQUEST);
        assertThat(reads.audit(id(), admin.getEmail(), 0, 20, from, from, null).total()).isZero();
    }
    @Test void historyFiltersOverRest() throws Exception {
        open();
        String base = "/api/v1/peladas/" + id() + "/finance";
        String token = bearerToken(admin.getId(), admin.getEmail());
        mockMvc.perform(get(base + "/audit?from=2026-10-31&to=2026-10-31&operations=PUBLISH&operations=CHARGE_SAVE").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.items.length()").value(2));
        mockMvc.perform(get(base + "/audit").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
        mockMvc.perform(get(base + "/audit?operations=NOPE").header("Authorization", token)).andExpect(status().isBadRequest());
        mockMvc.perform(get(base + "/audit?from=31/10/2026").header("Authorization", token)).andExpect(status().isBadRequest());
        mockMvc.perform(get(base + "/audit?from=2026-10-31&to=2026-10-01").header("Authorization", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Período inválido"));
        mockMvc.perform(get(base + "/audit?from=2026-10-01").header("Authorization", bearerToken(member.getId(), member.getEmail())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(base + "/movements?from=2026-10-01&to=2026-10-31").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get(base + "/movements?from=2026-10-31&to=2026-10-01").header("Authorization", token)).andExpect(status().isBadRequest());
    }
}
