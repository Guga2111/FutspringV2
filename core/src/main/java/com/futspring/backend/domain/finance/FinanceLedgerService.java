package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.finance.dto.*;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceAccess.*;
import static com.futspring.backend.domain.finance.FinanceTransactions.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;
import static com.futspring.backend.domain.finance.FinanceMoney.*;
import static com.futspring.backend.domain.finance.FinanceCollectionService.active;

@Service
@RequiredArgsConstructor
@Transactional
public class FinanceLedgerService {
    private final FinanceTransactions tx;
    private final FinanceReadService reads;
    private final FinanceAccess access;
    private final FinanceCollectionService collections;
    private final FinanceNoticeRepository notices;
    private final FinanceMovementRepository movements;
    private final FinanceFiles files;

    public FinanceViews.Result receive(Long id, Long listId, Long chargeId, FinanceRequests.Receipt request, String email) {
        return tx.execute(id, email, request.command(), "RECEIVE", "CHARGE:" + listId + ":" + chargeId, request, true, ctx -> {
            var list = reads.findList(id, listId); open(list);
            var charge = reads.findCharge(id, listId, chargeId); active(charge); collections.noPending(charge);
            BigDecimal amount = request.amount() == null ? reads.remaining(charge) : positive(request.amount());
            require(amount.signum() > 0 && amount.compareTo(reads.remaining(charge)) <= 0, "O recebimento deve caber no saldo restante");
            var before = reads.detail(ctx, listId);
            var movement = movement(ctx, MovementKind.RECEIPT, amount, date(ctx, request.effectiveDate()), request.method(), "Recebimento de " + charge.getPersonName());
            movement.setListId(listId); movement.setChargeId(chargeId); movements.save(movement);
            return new Change(before, reads.detail(ctx, listId), listId);
        });
    }
    public FinanceViews.Result report(Long id, Long listId, Long chargeId, FinanceRequests.Command command, MultipartFile file, String email) {
        FinanceFiles.Verified image = file == null ? null : files.verify(file);
        Object payload = List.of(command, image == null ? "" : image.hash());
        return tx.execute(id, email, command, "REPORT", "CHARGE:" + listId + ":" + chargeId, payload, false, ctx -> {
            var list = reads.findList(id, listId); open(list);
            var charge = reads.findCharge(id, listId, chargeId); active(charge);
            if (!Objects.equals(charge.getUserId(), ctx.caller().getId())) throw new AppException(HttpStatus.FORBIDDEN, "Informe apenas pagamentos próprios");
            collections.noPending(charge); BigDecimal amount = reads.remaining(charge);
            require(amount.signum() > 0, "Esta cobrança já está quitada");
            var before = reads.detail(ctx, listId);
            var notice = new FinanceNotice(); notice.setPeladaId(id); notice.setChargeId(chargeId); notice.setUserId(ctx.caller().getId());
            notice.setAmount(amount); notice.setCreatedAt(access.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
            if (image != null) {
                notice.setReceiptKey(files.store(image, false)); notice.setReceiptType(image.contentType());
                notice.setReceiptExpiresAt(notice.getCreatedAt().plus(Duration.ofDays(list.getClassification() == Classification.DAILY ? 7 : 30)));
            }
            notices.save(notice);
            return new Change(before, reads.detail(ctx, listId), listId);
        });
    }
    public FinanceViews.Result confirm(Long id, Long listId, Long chargeId, Long noticeId, FinanceRequests.Confirmation request, String email) {
        return tx.execute(id, email, request.command(), "CONFIRM", "NOTICE:" + listId + ":" + chargeId + ":" + noticeId, request, true, ctx -> {
            var list = reads.findList(id, listId); open(list);
            var charge = reads.findCharge(id, listId, chargeId); active(charge);
            var notice = notice(id, chargeId, noticeId); require(notice.getStatus() == NoticeStatus.PENDING, "Este aviso já foi conferido");
            require(notice.getAmount().compareTo(reads.remaining(charge)) <= 0, "O valor informado excede o saldo restante");
            var before = reads.detail(ctx, listId);
            var movement = movement(ctx, MovementKind.RECEIPT, notice.getAmount(), date(ctx, request.effectiveDate()), request.method(), "Pagamento conferido de " + charge.getPersonName());
            movement.setNoticeId(noticeId); movement.setChargeId(chargeId); movement.setListId(listId); movements.save(movement);
            notice.setStatus(NoticeStatus.CONFIRMED); notice.setResolvedBy(ctx.caller().getId()); notice.setResolvedAt(access.now());
            return new Change(before, reads.detail(ctx, listId), listId);
        });
    }
    public FinanceViews.Result reject(Long id, Long listId, Long chargeId, Long noticeId, FinanceRequests.Command command, String email) {
        return tx.execute(id, email, command, "REJECT", "NOTICE:" + listId + ":" + chargeId + ":" + noticeId, command, true, ctx -> {
            reads.findList(id, listId); reads.findCharge(id, listId, chargeId);
            var notice = notice(id, chargeId, noticeId); reason(command);
            require(notice.getStatus() == NoticeStatus.PENDING, "Este aviso já foi conferido");
            var before = reads.detail(ctx, listId); notice.setStatus(NoticeStatus.REJECTED); notice.setReason(command.reason());
            notice.setResolvedAt(access.now()); notice.setResolvedBy(ctx.caller().getId());
            return new Change(before, reads.detail(ctx, listId), listId);
        });
    }
    public FinanceViews.Result cash(Long id, FinanceRequests.Cash request, String email) {
        return tx.execute(id, email, request.command(), "CASH", "CASH", request, true, ctx -> {
            require(Set.of(MovementKind.OPENING, MovementKind.INCOME, MovementKind.EXPENSE).contains(request.kind()), "Tipo de movimentação inválido");
            BigDecimal amount = request.kind() == MovementKind.OPENING ? parse(request.amount()) : positive(request.amount());
            if (request.kind() == MovementKind.OPENING) {
                reason(request.command()); require(!movements.hasActiveOpening(id), "O saldo inicial já foi registrado; use uma anulação para corrigir");
                require(request.listId() == null && request.dailyId() == null, "O saldo inicial pertence à pelada");
            }
            if (request.kind() == MovementKind.EXPENSE) {
                require(request.category() != null && !request.category().isBlank(), "Informe a categoria da despesa"); amount = amount.negate();
            }
            var movement = movement(ctx, request.kind(), amount, date(ctx, request.effectiveDate()), request.method(), request.description().trim());
            movement.setCategory(request.category());
            if (request.listId() != null) { reads.findList(id, request.listId()); movement.setListId(request.listId()); }
            if (request.dailyId() != null) {
                var daily = reads.scopedDaily(id, request.dailyId()); movement.setDailyId(daily.getId());
                movement.setDailyLabel(daily.getDailyDate() + " " + daily.getDailyTime());
            }
            var before = text(movements.balance(id)); movements.save(movement);
            return new Change(before, reads.movementView(movement, false), request.listId());
        });
    }
    public FinanceViews.Result refund(Long id, Long listId, Long chargeId, FinanceRequests.Refund request, String email) {
        return tx.execute(id, email, request.command(), "REFUND", "CHARGE:" + listId + ":" + chargeId, request, true, ctx -> {
            reason(request.command()); reads.findList(id, listId);
            var charge = reads.findCharge(id, listId, chargeId); collections.noPending(charge);
            BigDecimal amount = positive(request.amount());
            require(!charge.isCanceled() || request.reduceObligation(), "Uma cobrança cancelada exige reduzir a obrigação na devolução");
            require(amount.compareTo(reads.paid(charge)) <= 0, "A devolução não pode passar do recebido");
            var before = reads.detail(ctx, listId);
            var movement = movement(ctx, MovementKind.REFUND, amount.negate(), date(ctx, request.effectiveDate()), request.method(), "Devolução a " + charge.getPersonName());
            movement.setChargeId(chargeId); movement.setListId(listId);
            if (request.reduceObligation()) {
                movement.setObligationDelta(amount.negate()); charge.setAmountDue(charge.getAmountDue().subtract(amount));
                charge.setAdjustment(check(charge.getAdjustment().subtract(amount)));
            }
            movements.save(movement); return new Change(before, reads.detail(ctx, listId), listId);
        });
    }
    public FinanceViews.Result reverse(Long id, Long movementId, FinanceRequests.Command command, String email) {
        return tx.execute(id, email, command, "REVERSE", "MOVEMENT:" + movementId, command, true, ctx -> {
            reason(command); var original = movements.findByIdAndPeladaId(movementId, id).orElseThrow(() -> missing("Movimentação"));
            require(original.getKind() != MovementKind.REVERSAL && !movements.existsByOriginalId(movementId), "Este lançamento já foi anulado ou é uma anulação");
            var reversal = movement(ctx, MovementKind.REVERSAL, original.getAmount().negate(), original.getEffectiveDate(), original.getMethod(), ("Anulação: " + original.getDescription()).substring(0, Math.min(500, 10 + original.getDescription().length())));
            reversal.setOriginalId(movementId); reversal.setListId(original.getListId()); reversal.setDailyId(original.getDailyId()); reversal.setDailyLabel(original.getDailyLabel());
            reversal.setChargeId(original.getChargeId()); reversal.setObligationDelta(original.getObligationDelta().negate());
            if (original.getChargeId() != null) {
                var charge = reads.findCharge(id, original.getListId(), original.getChargeId()); collections.noPending(charge);
                BigDecimal resultingPaid = reads.paid(charge).add(reversal.getAmount());
                if (charge.isCanceled()) reversal.setObligationDelta(resultingPaid.subtract(charge.getAmountDue()));
                BigDecimal resultingDue = charge.getAmountDue().add(reversal.getObligationDelta());
                require(resultingPaid.signum() >= 0 && resultingDue.compareTo(resultingPaid) >= 0,
                        "A anulação deixaria a obrigação incompatível; corrija os ajustes ou devoluções primeiro");
                charge.setAmountDue(check(resultingDue)); charge.setAdjustment(check(charge.getAdjustment().add(reversal.getObligationDelta())));
            }
            movements.save(reversal);
            return new Change(reads.movementView(original, false), reads.movementView(reversal, false), original.getListId());
        });
    }
    private FinanceNotice notice(Long id, Long chargeId, Long noticeId) {
        var n = notices.findByIdAndPeladaId(noticeId, id).orElseThrow(() -> missing("Aviso"));
        if (!n.getChargeId().equals(chargeId)) throw missing("Aviso"); return n;
    }
    private FinanceMovement movement(Context ctx, MovementKind kind, BigDecimal amount, LocalDate effective, PaymentMethod method, String description) {
        var movement = new FinanceMovement(); movement.setPeladaId(ctx.pelada().getId()); movement.setKind(kind); movement.setAmount(check(amount));
        movement.setEffectiveDate(effective); movement.setMethod(method); movement.setDescription(description); movement.setCreatedBy(ctx.caller().getId()); movement.setCreatedAt(access.now());
        return movement;
    }
    private LocalDate date(Context ctx, LocalDate value) {
        LocalDate date = value == null ? access.today(ctx) : value;
        require(!date.isAfter(access.today(ctx)), "A data efetiva não pode estar no futuro"); return date;
    }
    private static void open(FinanceList list) { require(list.getStatus() == ListStatus.OPEN, "Reabra a lista para informar ou receber pagamentos"); }
}
