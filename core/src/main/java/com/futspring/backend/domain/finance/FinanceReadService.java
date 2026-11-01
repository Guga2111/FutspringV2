package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.finance.dto.FinanceViews;
import com.futspring.backend.domain.pelada.PeladaMensalistaRepository;
import com.futspring.backend.domain.daily.repository.*;
import com.futspring.backend.domain.daily.entity.Daily;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.futspring.backend.shared.exception.AppException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import static com.futspring.backend.domain.finance.FinanceTypes.*;
import static com.futspring.backend.domain.finance.FinanceMoney.*;
import static com.futspring.backend.domain.finance.FinanceAccess.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinanceReadService {
    private final FinanceAccess access;
    private final FinanceListRepository lists;
    private final FinanceChargeRepository charges;
    private final FinanceNoticeRepository notices;
    private final FinanceMovementRepository movements;
    private final FinanceTemplateRepository templates;
    private final FinanceManagerRepository managers;
    private final FinanceAuditRepository audits;
    private final DailyRepository dailies;
    private final DailyAttendanceRepository attendance;
    private final PeladaMensalistaRepository mensalistas;

    public FinanceViews.Overview overview(Long id, String email, int page, int size) {
        return overview(access.read(id, email), page, size);
    }
    FinanceViews.Overview overview(Context ctx, int page, int size) {
        int limit = limit(size);
        // Filtering happens in SQL before pagination, so private drafts cannot affect a member's page.
        var rows = lists.visible(ctx.pelada().getId(), ctx.manage(), ctx.viewCash(), ctx.caller().getId(),
                PageRequest.of(page(page), limit));
        List<FinanceList> shown = rows.stream().limit(limit).toList();
        var allCharges = shown.isEmpty() ? List.<FinanceCharge>of() : charges.findByListIdIn(shown.stream().map(FinanceList::getId).toList());
        var data = data(allCharges);
        return new FinanceViews.Overview(ctx.account().getRevision(),
                new FinanceViews.Permissions(ctx.manage(), ctx.appoint(), ctx.viewCash()),
                new FinanceViews.Settings(ctx.account().getVisibility(), ctx.account().getPixKey(),
                        ctx.account().getPixRecipient(), ctx.account().getQrId(), ctx.manage()
                        ? managers.findByPeladaId(ctx.pelada().getId()).stream().map(FinanceManager::getUserId).toList() : List.of()),
                ctx.viewCash() ? text(movements.balance(ctx.pelada().getId())) : null,
                ctx.manage() ? templateViews(ctx) : List.of(),
                shown.stream().map(list -> collection(list, visibleCharges(ctx, allCharges.stream()
                        .filter(c -> c.getListId().equals(list.getId())).toList()), data, false)).toList(), rows.hasNext());
    }
    public FinanceViews.Detail detail(Long id, Long listId, String email) { return detail(access.read(id, email), listId); }
    FinanceViews.Detail detail(Context ctx, Long listId) {
        FinanceList list = findList(ctx.pelada().getId(), listId);
        requireVisible(ctx, list);
        List<FinanceCharge> rows = visibleCharges(ctx, charges.findByListIdOrderById(listId));
        var data = data(rows);
        return new FinanceViews.Detail(collection(list, rows, data), rows.stream().map(c -> charge(ctx, c, data)).toList());
    }
    private List<FinanceCharge> visibleCharges(Context ctx, List<FinanceCharge> rows) {
        return ctx.viewCash() ? rows : rows.stream().filter(c -> Objects.equals(c.getUserId(), ctx.caller().getId())).toList();
    }
    void requireVisible(Context ctx, FinanceList list) {
        if (!ctx.manage() && list.getStatus() == ListStatus.DRAFT) throw missing("Lista");
        if (!ctx.viewCash() && charges.findByListIdOrderById(list.getId()).stream()
                .noneMatch(c -> Objects.equals(c.getUserId(), ctx.caller().getId()))) throw missing("Lista");
    }
    FinanceList findList(Long peladaId, Long id) { return lists.findByIdAndPeladaId(id, peladaId).orElseThrow(() -> missing("Lista")); }
    FinanceCharge findCharge(Long peladaId, Long listId, Long id) {
        return charges.findByIdAndPeladaIdAndListId(id, peladaId, listId).orElseThrow(() -> missing("Cobrança"));
    }
    record Data(Map<Long, BigDecimal> paid, Map<Long, List<FinanceNotice>> notices) {}
    private Data data(List<FinanceCharge> rows) {
        if (rows.isEmpty()) return new Data(Map.of(), Map.of());
        var ids = rows.stream().map(FinanceCharge::getId).toList();
        Map<Long, BigDecimal> paid = new HashMap<>();
        movements.findByChargeIdIn(ids).forEach(m -> paid.merge(m.getChargeId(), m.getAmount(), BigDecimal::add));
        return new Data(paid, notices.recentByChargeIds(ids).stream().collect(Collectors.groupingBy(FinanceNotice::getChargeId)));
    }
    BigDecimal paid(FinanceCharge charge) { return data(List.of(charge)).paid().getOrDefault(charge.getId(), ZERO); }
    BigDecimal remaining(FinanceCharge charge) { return charge.getAmountDue().subtract(paid(charge)); }
    FinanceViews.Collection collection(FinanceList list, List<FinanceCharge> rows, Data data) { return collection(list, rows, data, true); }
    private FinanceViews.Collection collection(FinanceList list, List<FinanceCharge> rows, Data data, boolean includeComponents) {
        BigDecimal due = ZERO, received = ZERO;
        long pending = 0;
        for (var charge : rows) {
            {
                due = due.add(charge.getAmountDue());
                received = received.add(data.paid().getOrDefault(charge.getId(), ZERO));
                pending += data.notices().getOrDefault(charge.getId(), List.of()).stream().filter(n -> n.getStatus() == NoticeStatus.PENDING).count();
            }
        }
        return new FinanceViews.Collection(list.getId(), list.getName(), list.getClassification(), list.getStatus(),
                list.getDescription(), list.getDueDate(), list.getDailyId(), list.getDailyLabel(), list.getPixKey(),
                list.getPixRecipient(), list.getQrId(), includeComponents ? componentViews(list.getComponents()) : List.of(), list.getPublishedAt(),
                new FinanceViews.Totals(text(due), text(received), text(due.subtract(received)), pending));
    }
    FinanceViews.Charge charge(Context ctx, FinanceCharge charge, Data data) {
        BigDecimal received = data.paid().getOrDefault(charge.getId(), ZERO);
        BigDecimal outstanding = charge.getAmountDue().subtract(received);
        return new FinanceViews.Charge(charge.getId(), charge.getPersonId(), charge.getUserId(), charge.getPersonName(),
                componentViews(charge.getComponents()), text(charge.getAdjustment()), text(charge.getAmountDue()), text(received), text(outstanding),
                outstanding.signum() == 0 ? PaymentStatus.PAID : received.signum() > 0 ? PaymentStatus.PARTIAL : PaymentStatus.UNPAID,
                !charge.isCanceled() && outstanding.signum() > 0 && charge.getDueDate() != null && charge.getDueDate().isBefore(access.today(ctx)),
                charge.isCanceled(), charge.getDueDate(), ctx.manage() || Objects.equals(ctx.caller().getId(), charge.getUserId())
                ? data.notices().getOrDefault(charge.getId(), List.of()).stream().map(this::noticeView).toList() : List.of());
    }
    FinanceViews.Notice noticeView(FinanceNotice n) {
        return new FinanceViews.Notice(n.getId(), n.getUserId(), text(n.getAmount()), n.getStatus(), n.getCreatedAt(),
                n.getResolvedAt(), n.getResolvedBy(), n.getReason(), n.getReceiptExpiresAt(),
                n.getReceiptKey() != null && n.getReceiptDeletedAt() == null && access.now().isBefore(n.getReceiptExpiresAt()));
    }
    static List<FinanceViews.Component> componentViews(List<PriceComponent> items) {
        return items.stream().map(c -> new FinanceViews.Component(c.getName(), text(c.getAmount()))).toList();
    }
    FinanceViews.Template templateView(FinanceTemplate t) {
        return new FinanceViews.Template(t.getId(), t.getName(), t.getClassification(), t.getDescription(), componentViews(t.getComponents()),
                t.getPixKey(), t.getPixRecipient(), t.getQrId(), t.isArchived());
    }
    private List<FinanceViews.Template> templateViews(Context ctx) {
        var saved = templates.findByPeladaIdOrderById(ctx.pelada().getId());
        if (!saved.isEmpty()) return saved.stream().map(this::templateView).toList();
        return List.of(defaultTemplate("Mensalidade", Classification.MONTHLY, ctx), defaultTemplate("Diária", Classification.DAILY, ctx),
                defaultTemplate("Evento", Classification.OTHER, ctx));
    }
    private FinanceViews.Template defaultTemplate(String name, Classification kind, Context ctx) {
        return new FinanceViews.Template(null, name, kind, "", List.of(new FinanceViews.Component("Campo", "0.00")),
                ctx.account().getPixKey(), ctx.account().getPixRecipient(), ctx.account().getQrId(), false);
    }
    public FinanceViews.Page<FinanceViews.Movement> ledger(Long id, String email, int page, int size) {
        var ctx = access.read(id, email);
        if (!ctx.viewCash()) throw new AppException(HttpStatus.FORBIDDEN, "O extrato é restrito aos gestores neste modo");
        var rows = movements.findByPeladaIdOrderByIdDesc(id, PageRequest.of(page(page), limit(size)));
        Set<Long> reversed = rows.isEmpty() ? Set.of() : movements.reversedIds(rows.stream().map(FinanceMovement::getId).toList());
        return new FinanceViews.Page<>(rows.stream().limit(limit(size)).map(m -> movementView(m, reversed.contains(m.getId()))).toList(), rows.hasNext());
    }
    FinanceViews.Movement movementView(FinanceMovement m, boolean reversed) {
        return new FinanceViews.Movement(m.getId(), m.getListId(), m.getDailyId(), m.getDailyLabel(), m.getChargeId(), m.getNoticeId(),
                m.getOriginalId(), m.getKind(), text(m.getAmount()), text(m.getObligationDelta()), m.getEffectiveDate(), m.getMethod(),
                m.getDescription(), m.getCategory(), m.getCreatedBy(), m.getCreatedAt(), reversed);
    }
    public FinanceViews.Page<FinanceViews.Notice> noticeHistory(Long id, Long listId, Long chargeId, String email, int page, int size) {
        var ctx = access.read(id, email); var list = findList(id, listId); requireVisible(ctx, list);
        var charge = findCharge(id, listId, chargeId);
        if (!ctx.manage() && !Objects.equals(charge.getUserId(), ctx.caller().getId())) throw missing("Cobrança");
        var rows = notices.findByPeladaIdAndChargeIdOrderByIdDesc(id, chargeId, PageRequest.of(page(page), limit(size)));
        return new FinanceViews.Page<>(rows.stream().map(this::noticeView).toList(), rows.hasNext());
    }
    public FinanceViews.Page<FinanceViews.Audit> audit(Long id, String email, int page, int size) {
        var ctx = access.read(id, email); ctx.requireManager();
        var rows = audits.findByPeladaIdOrderByIdDesc(id, PageRequest.of(page(page), limit(size)));
        return new FinanceViews.Page<>(rows.stream().limit(limit(size)).map(a -> new FinanceViews.Audit(a.getId(), a.getActorId(),
                a.getActorName(), a.getOperation(), a.getTarget(), a.getReason(), a.getCreatedAt(), a.getBeforeData(), a.getAfterData())).toList(), rows.hasNext());
    }
    public List<FinanceViews.Candidate> importCandidates(Long id, Long dailyId, String email) {
        var ctx = access.read(id, email); ctx.requireManager();
        Daily daily = scopedDaily(id, dailyId);
        Set<Long> monthly = mensalistas.findUserIdsByPelada(ctx.pelada());
        return attendance.findUsersByDaily(daily).stream().map(u -> new FinanceViews.Candidate(u.getId(), u.getUsername(), monthly.contains(u.getId()))).toList();
    }
    Daily scopedDaily(Long id, Long dailyId) {
        Daily daily = dailies.findById(dailyId).orElseThrow(() -> missing("Diária"));
        if (!daily.getPelada().getId().equals(id)) throw missing("Diária");
        return daily;
    }
    static int limit(int size) { return Math.clamp(size, 1, 50); }
    static int page(int page) { return Math.clamp(page, 0, 1000000); }
}
