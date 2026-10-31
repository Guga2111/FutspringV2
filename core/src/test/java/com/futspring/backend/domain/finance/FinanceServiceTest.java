package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.finance.dto.*;
import com.futspring.backend.domain.pelada.*;
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
    @Autowired PeladaRepository peladas;
    @Autowired PeladaService peladaService;
    @Autowired UserRepository users;
    @Autowired DailyRepository dailies;
    @Autowired DailyService dailyService;
    @Autowired jakarta.persistence.EntityManager em;
    User admin, member, other, outsider;
    Pelada pelada;
    @BeforeEach void setup() {
        clock.now = NOW;
        admin = user("Gestor"); member = user("Mensalista"); other = user("Diarista"); outsider = user("Visitante");
        pelada = peladas.save(Pelada.builder().name("Pelada financeira").dayOfWeek("SATURDAY").timeOfDay("19:00").duration(1f)
                .creator(admin).members(new HashSet<>(Set.of(admin, member, other))).admins(new HashSet<>(Set.of(admin))).build());
    }
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
    @Test void offlinePartialDiscountRefundAndReversalReconcileDebtCashAndAudit() {
        Long l = open(); receive(l, "20.00");
        assertThat(view(l).paymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        collections.charge(id(), l, charge(l), false, new FinanceRequests.Charge(command(), null, null, null, price(), "-5.00", null), admin.getEmail());
        receive(l, "20.00");
        assertThat(view(l).paymentStatus()).isEqualTo(PaymentStatus.PAID);
        var refund = ledger.refund(id(), l, charge(l), new FinanceRequests.Refund(command(), "10.00", null, PaymentMethod.CASH, true), admin.getEmail());
        assertThat(refund.overview().balance()).isEqualTo("30.00"); assertThat(view(l).amountDue()).isEqualTo("30.00");
        Long refundId = reads.ledger(id(), admin.getEmail(), 0, 20).items().getFirst().id();
        ledger.reverse(id(), refundId, command(), admin.getEmail());
        assertThat(view(l).amountDue()).isEqualTo("40.00"); assertThat(view(l).received()).isEqualTo("40.00");
        assertThat(reads.overview(id(), admin.getEmail(), 0, 50).balance()).isEqualTo("40.00");
        assertThat(reads.audit(id(), admin.getEmail(), 0, 50).items()).allSatisfy(a -> assertThat(a.actorId()).isEqualTo(admin.getId()));
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
        fails(() -> reads.ledger(id(), member.getEmail(), 0, 20), HttpStatus.FORBIDDEN);
        fails(() -> reads.audit(id(), member.getEmail(), 0, 20), HttpStatus.FORBIDDEN);
        collections.settings(id(), new FinanceRequests.Settings(command(), Visibility.TRANSPARENT, "novo", "Novo", null), admin.getEmail());
        var visible = reads.detail(id(), l, other.getEmail()); assertThat(visible.charges()).hasSize(2);
        assertThat(visible.charges().getFirst().notices()).isEmpty();
        assertThat(visible.collection().pixKey()).isEqualTo("pix-original");
        assertThat(reads.overview(id(), other.getEmail(), 0, 50).lists()).noneMatch(row -> row.id().equals(hidden));
    }
    @Test void delegatesCannotAppointManagersAndRemovalRevokesAccessButKeepsIdentity() {
        Long l = open(); collections.managers(id(), new FinanceRequests.Managers(command(), List.of(member.getId())), admin.getEmail());
        assertThat(reads.overview(id(), member.getEmail(), 0, 50).permissions().manage()).isTrue();
        fails(() -> collections.managers(id(), new FinanceRequests.Managers(command(), List.of(other.getId())), member.getEmail()), HttpStatus.FORBIDDEN);
        Long identity = view(l).personId();
        peladaService.removePlayer(id(), member.getId(), admin.getEmail());
        fails(() -> reads.overview(id(), member.getEmail(), 0, 50), HttpStatus.FORBIDDEN);
        assertThat(view(l).personId()).isEqualTo(identity);
        pelada.getMembers().add(member); peladas.saveAndFlush(pelada);
        assertThat(reads.overview(id(), member.getEmail(), 0, 50).permissions().manage()).isFalse();
        assertThat(reads.detail(id(), l, member.getEmail()).charges().getFirst().personId()).isEqualTo(identity);
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
        Long opening = reads.ledger(id(), admin.getEmail(), 0, 20).items().getFirst().id();
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
}
