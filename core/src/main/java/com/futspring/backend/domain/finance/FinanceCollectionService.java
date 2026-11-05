package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.finance.dto.*;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceTypes.*;
import static com.futspring.backend.domain.finance.FinanceAccess.*;
import static com.futspring.backend.domain.finance.FinanceTransactions.*;
import static com.futspring.backend.domain.finance.FinanceMoney.*;

@Service
@RequiredArgsConstructor
@Transactional
public class FinanceCollectionService {
    private final FinanceTransactions tx;
    private final FinanceReadService reads;
    private final FinanceAccess access;
    private final FinanceTemplateRepository templates;
    private final FinanceListRepository lists;
    private final FinanceChargeRepository charges;
    private final FinancePersonRepository people;
    private final FinanceNoticeRepository notices;
    private final FinanceManagerRepository managers;
    private final FinanceQrRepository qrs;
    private final PeladaRepository peladas;
    private final UserRepository users;

    public FinanceViews.Result settings(Long id, FinanceRequests.Settings request, String email) {
        return tx.execute(id, email, request.command(), "SETTINGS", "SETTINGS", request, true, ctx -> {
            var before = reads.overview(ctx, 0, 1).settings();
            ctx.account().setVisibility(request.visibility()); ctx.account().setPixKey(request.pixKey());
            ctx.account().setPixRecipient(request.pixRecipient());
            validateQr(id, request.qrId()); ctx.account().setQrId(request.qrId());
            return new Change(before, reads.overview(ctx, 0, 1).settings(), null);
        });
    }
    public FinanceViews.Result managers(Long id, FinanceRequests.Managers request, String email) {
        return tx.execute(id, email, request.command(), "MANAGERS", "MANAGERS", request, true, ctx -> {
            if (!ctx.appoint()) throw new AppException(HttpStatus.FORBIDDEN, "Apenas administradores podem designar responsáveis");
            Set<Long> ids = new LinkedHashSet<>(request.userIds());
            require(ids.size() == request.userIds().size(), "Não repita responsáveis");
            require(ids.isEmpty() || peladas.findMemberIds(id, ids).size() == ids.size(), "Responsáveis precisam ser membros da pelada");
            var before = managers.findByPeladaId(id).stream().map(FinanceManager::getUserId).toList();
            managers.deleteAll(managers.findByPeladaId(id)); managers.flush();
            ids.forEach(userId -> { var m = new FinanceManager(); m.setPeladaId(id); m.setUserId(userId); managers.save(m); });
            return new Change(before, ids, null);
        });
    }
    public FinanceViews.Result template(Long id, Long templateId, FinanceRequests.Template request, String email) {
        return tx.execute(id, email, request.command(), "TEMPLATE", "TEMPLATE:" + templateId, request, true, ctx -> {
            var t = templateId == null ? new FinanceTemplate() : templates.findByIdAndPeladaId(templateId, id).orElseThrow(() -> missing("Modelo"));
            var before = templateId == null ? null : reads.templateView(t);
            validateQr(id, request.qrId());
            t.setPeladaId(id); t.setName(request.name().trim()); t.setClassification(request.classification());
            t.setDescription(request.description()); t.setPixKey(request.pixKey()); t.setPixRecipient(request.pixRecipient());
            t.setQrId(request.qrId()); t.setArchived(request.archived());
            t.getComponents().clear(); t.getComponents().addAll(components(request.components()));
            templates.save(t);
            return new Change(before, reads.templateView(t), null);
        });
    }
    public FinanceViews.Result collection(Long id, Long listId, FinanceRequests.Collection request, String email) {
        return tx.execute(id, email, request.command(), "LIST_SAVE", "LIST:" + listId, request, true, ctx -> {
            var list = listId == null ? new FinanceList() : reads.findList(id, listId);
            var before = listId == null ? null : reads.detail(ctx, listId);
            if (listId != null) {
                require(list.getStatus() != ListStatus.CANCELED, "A lista está cancelada");
                if (list.getPublishedAt() != null) {
                    reason(request.command());
                    require(list.getClassification() == request.classification(), "A classificação de uma lista publicada não pode mudar");
                    require(componentsEqual(list.getComponents(), request.components()), "Altere os valores nas cobranças individuais");
                }
            }
            saveCollection(ctx, list, listId, request);
            return new Change(before, reads.detail(ctx, list.getId()), list.getId());
        });
    }
    /** A complete organizer submission commits the collection, allocations and publication together. */
    public FinanceViews.Result createCollection(Long id, FinanceRequests.CreateCollection request, String email) {
        var collection = request.collection();
        return tx.execute(id, email, collection.command(), "LIST_SAVE", "LIST_WITH_PARTICIPANTS:null", request, true, ctx -> {
            var list = new FinanceList();
            saveCollection(ctx, list, null, collection);
            if (!request.userIds().isEmpty() || !request.guestNames().isEmpty()) {
                addParticipants(ctx, list, new FinanceRequests.Participants(collection.command(), request.userIds(), request.guestNames()));
            }
            if (request.publish()) {
                require(!request.userIds().isEmpty() || !request.guestNames().isEmpty(), "Selecione quem deve pagar antes de publicar");
                list.setStatus(ListStatus.OPEN);
                list.setPublishedAt(access.now());
            }
            return new Change(null, reads.detail(ctx, list.getId()), list.getId());
        });
    }
    private void saveCollection(Context ctx, FinanceList list, Long listId, FinanceRequests.Collection request) {
        validateQr(ctx.pelada().getId(), request.qrId());
        list.setPeladaId(ctx.pelada().getId()); list.setName(request.name().trim()); list.setClassification(request.classification());
        list.setDescription(request.description()); list.setDueDate(request.dueDate());
        if (request.dailyId() != null) {
            var daily = reads.scopedDaily(ctx.pelada().getId(), request.dailyId());
            list.setDailyId(daily.getId()); list.setDailyLabel(daily.getDailyDate() + " " + daily.getDailyTime());
        } else list.setDailyId(null); // Historical label is retained when detached.
        list.setPixKey(request.pixKey() == null && listId == null ? ctx.account().getPixKey() : request.pixKey());
        list.setPixRecipient(request.pixRecipient() == null && listId == null ? ctx.account().getPixRecipient() : request.pixRecipient());
        list.setQrId(request.qrId() == null && listId == null ? ctx.account().getQrId() : request.qrId());
        list.getComponents().clear(); list.getComponents().addAll(components(request.components()));
        if (listId == null) list.setCreatedAt(access.now());
        lists.save(list);
    }
    public FinanceViews.Result transition(Long id, Long listId, String operation, FinanceRequests.Command command, String email) {
        return tx.execute(id, email, command, operation, "LIST:" + listId, command, true, ctx -> {
            var list = reads.findList(id, listId); var before = reads.detail(ctx, listId);
            var chargeRows = charges.findByListIdOrderById(listId);
            var ids = chargeRows.stream().map(FinanceCharge::getId).toList();
            // One query for the whole list: a pending notice on any charge blocks close/cancel
            boolean pending = !ids.isEmpty() && !notices.chargeIdsWithStatus(ids, NoticeStatus.PENDING).isEmpty();
            switch (operation) {
                case "PUBLISH" -> {
                    require(list.getStatus() == ListStatus.DRAFT, "Apenas rascunhos podem ser publicados");
                    require(chargeRows.stream().anyMatch(c -> !c.isCanceled()), "Adicione participantes antes de publicar");
                    list.setStatus(ListStatus.OPEN); list.setPublishedAt(access.now());
                }
                case "CLOSE" -> {
                    require(list.getStatus() == ListStatus.OPEN, "A lista precisa estar aberta");
                    require(!pending, "Resolva todos os avisos pendentes antes de encerrar"); list.setStatus(ListStatus.CLOSED);
                }
                case "REOPEN" -> {
                    reason(command); require(list.getStatus() == ListStatus.CLOSED, "A lista precisa estar encerrada"); list.setStatus(ListStatus.OPEN);
                }
                case "CANCEL" -> {
                    reason(command); require(list.getStatus() == ListStatus.DRAFT || list.getStatus() == ListStatus.OPEN || list.getStatus() == ListStatus.CLOSED,
                            "A lista já está cancelada");
                    require(!pending, "Resolva os avisos pendentes antes de cancelar");
                    require(reads.paid(chargeRows).values().stream().allMatch(v -> v.signum() == 0), "A lista tem recebimentos confirmados");
                    // Even a fully refunded receipt is still a confirmed historical receipt unless reversed.
                    require(!hasValidReceipts(chargeRows), "Anule os recebimentos registrados por engano antes de cancelar");
                    list.setStatus(ListStatus.CANCELED);
                }
                default -> throw new IllegalArgumentException(operation);
            }
            return new Change(before, reads.detail(ctx, listId), listId);
        });
    }
    private final FinanceMovementRepository movements;
    private boolean hasValidReceipts(List<FinanceCharge> rows) {
        if (rows.isEmpty()) return false;
        var entries = movements.findByChargeIdIn(rows.stream().map(FinanceCharge::getId).toList());
        Set<Long> reversed = entries.stream().map(FinanceMovement::getOriginalId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return entries.stream().anyMatch(m -> m.getKind() == MovementKind.RECEIPT && !reversed.contains(m.getId()));
    }
    public FinanceViews.Result duplicate(Long id, Long listId, FinanceRequests.Command command, String email) {
        return tx.execute(id, email, command, "DUPLICATE", "LIST:" + listId, command, true, ctx -> {
            var original = reads.findList(id, listId);
            var copy = new FinanceList(); copy.setPeladaId(id); copy.setName(original.getName()); copy.setDescription(original.getDescription());
            copy.setClassification(original.getClassification()); copy.setDueDate(original.getDueDate());
            copy.setDailyId(original.getDailyId()); copy.setDailyLabel(original.getDailyLabel());
            copy.setPixKey(original.getPixKey()); copy.setPixRecipient(original.getPixRecipient()); copy.setQrId(original.getQrId());
            copy.setCreatedAt(access.now()); copy.getComponents().addAll(copyComponents(original.getComponents())); lists.save(copy);
            for (var charge : charges.findByListIdOrderById(listId)) {
                if (charge.isCanceled()) continue;
                var c = new FinanceCharge(); c.setPeladaId(id); c.setListId(copy.getId()); c.setPersonId(charge.getPersonId());
                c.setUserId(charge.getUserId()); c.setPersonName(charge.getPersonName()); c.setAdjustment(charge.getAdjustment());
                c.setAmountDue(charge.getAmountDue()); c.setDueDate(charge.getDueDate()); c.getComponents().addAll(copyComponents(charge.getComponents())); charges.save(c);
            }
            return new Change(null, reads.detail(ctx, copy.getId()), copy.getId());
        });
    }
    public FinanceViews.Result participants(Long id, Long listId, FinanceRequests.Participants request, String email) {
        return tx.execute(id, email, request.command(), "PARTICIPANTS", "LIST:" + listId, request, true, ctx -> {
            var list = reads.findList(id, listId); require(list.getStatus() != ListStatus.CANCELED, "A lista está cancelada");
            if (list.getPublishedAt() != null) reason(request.command());
            var before = reads.detail(ctx, listId);
            addParticipants(ctx, list, request);
            return new Change(before, reads.detail(ctx, listId), listId);
        });
    }
    // Batch of new charges with the list's defaults: identities, conflicts and names are read once for the whole batch,
    // and every check runs before the first write
    private void addParticipants(Context ctx, FinanceList list, FinanceRequests.Participants request) {
        Long id = ctx.pelada().getId();
        Long listId = list.getId();
        require(!request.userIds().isEmpty() || !request.guestNames().isEmpty(), "Selecione participantes");
        require(new HashSet<>(request.userIds()).size() == request.userIds().size(), "Não repita jogadores");
        require(request.userIds().isEmpty() || peladas.findMemberIds(id, request.userIds()).size() == request.userIds().size(), "Selecione membros desta pelada");
        require(request.guestNames().stream().noneMatch(String::isBlank), "Selecione um membro ou informe um convidado");
        // A returning member keeps their financial identity
        Map<Long, FinancePerson> known = new HashMap<>();
        if (!request.userIds().isEmpty()) people.findByPeladaIdAndUserIdIn(id, request.userIds()).forEach(p -> known.put(p.getUserId(), p));
        // Only an existing identity can already have a charge here; the first one, in request order, fails the batch
        if (!known.isEmpty()) {
            var active = new HashSet<>(charges.findActivePersonIds(listId, known.values().stream().map(FinancePerson::getId).toList()));
            request.userIds().stream().map(known::get).filter(p -> p != null && active.contains(p.getId())).findFirst()
                    .ifPresent(p -> { throw new AppException(HttpStatus.CONFLICT, p.getName() + " já tem cobrança nesta lista"); });
        }
        var unknown = request.userIds().stream().filter(userId -> !known.containsKey(userId)).toList();
        Map<Long, String> names = new HashMap<>();
        if (!unknown.isEmpty()) users.findAllById(unknown).forEach(u -> names.put(u.getId(), u.getUsername()));
        List<FinancePerson> created = new ArrayList<>(), participants = new ArrayList<>();
        for (Long userId : request.userIds()) {
            var p = known.get(userId);
            if (p == null) {
                String name = names.get(userId);
                if (name == null) throw missing("Jogador");
                p = newPerson(id, userId, name); created.add(p);
            }
            participants.add(p);
        }
        // Guests always get a new identity, even with equal names
        for (String name : request.guestNames()) { var p = newPerson(id, null, name.trim()); created.add(p); participants.add(p); }
        people.saveAll(created);
        BigDecimal due = check(list.getComponents().stream().map(PriceComponent::getAmount).reduce(ZERO, BigDecimal::add));
        charges.saveAll(participants.stream().map(person -> {
            var charge = new FinanceCharge(); charge.setPeladaId(id); charge.setListId(listId); charge.setPersonId(person.getId());
            charge.setUserId(person.getUserId()); charge.setPersonName(person.getName()); charge.setDueDate(list.getDueDate());
            charge.getComponents().addAll(copyComponents(list.getComponents())); charge.setAmountDue(due);
            return charge;
        }).toList());
    }
    private static FinancePerson newPerson(Long peladaId, Long userId, String name) {
        var p = new FinancePerson(); p.setPeladaId(peladaId); p.setUserId(userId); p.setName(name); return p;
    }
    public FinanceViews.Result charge(Long id, Long listId, Long chargeId, boolean replace, FinanceRequests.Charge request, String email) {
        return tx.execute(id, email, request.command(), replace ? "CHARGE_REPLACE" : "CHARGE_SAVE", "CHARGE:" + listId + ":" + chargeId, request, true, ctx -> {
            var list = reads.findList(id, listId); require(list.getStatus() != ListStatus.CANCELED, "A lista está cancelada");
            var before = reads.detail(ctx, listId);
            if (list.getPublishedAt() != null) reason(request.command());
            var previous = chargeId == null ? null : reads.findCharge(id, listId, chargeId);
            if (previous != null) { active(previous); noPending(previous); }
            FinanceCharge c;
            if (previous == null || replace) {
                if (replace) {
                    // The old allocation keeps its money; only its unpaid obligation is canceled.
                    BigDecimal retained = reads.paid(previous);
                    previous.setAdjustment(check(previous.getAdjustment().subtract(previous.getAmountDue().subtract(retained))));
                    previous.setAmountDue(retained); previous.setCanceled(true); charges.flush();
                }
                FinancePerson person = person(ctx, request);
                require(!charges.existsByListIdAndPersonIdAndCanceledFalse(listId, person.getId()), "Esta pessoa já tem cobrança nesta lista");
                c = new FinanceCharge(); c.setPeladaId(id); c.setListId(listId); c.setPersonId(person.getId());
                c.setUserId(person.getUserId()); c.setPersonName(person.getName());
            } else {
                c = previous;
                require(request.userId() == null || Objects.equals(request.userId(), c.getUserId()), "Use a ação de trocar participante");
                require(request.personId() == null || Objects.equals(request.personId(), c.getPersonId()), "Use a ação de trocar participante");
                require(request.guestName() == null || request.guestName().isBlank() || request.guestName().trim().equals(c.getPersonName()), "Use a ação de trocar participante");
            }
            c.getComponents().clear(); c.getComponents().addAll(components(request.components())); c.setAdjustment(parse(request.adjustment()));
            BigDecimal total = check(c.getComponents().stream().map(PriceComponent::getAmount).reduce(ZERO, BigDecimal::add).add(c.getAdjustment()));
            require(total.signum() >= 0 && total.compareTo(previous == null || replace ? ZERO : reads.paid(c)) >= 0,
                    "O valor devido não pode ser menor que o recebido");
            c.setAmountDue(total); c.setDueDate(request.dueDate()); charges.save(c);
            return new Change(before, reads.detail(ctx, listId), listId);
        });
    }
    // "Ajustar valor": every listed charge gets the same components and adjustment, in one revision and one audit entry.
    // All or nothing: a charge from another list, canceled, waiting for a notice review or already paid above the new
    // value fails the batch. Due dates and identities stay as they are.
    public FinanceViews.Result adjustCharges(Long id, Long listId, FinanceRequests.BulkAdjust request, String email) {
        return tx.execute(id, email, request.command(), "CHARGE_SAVE", "LIST:" + listId, request, true, ctx -> {
            var list = reads.findList(id, listId); require(list.getStatus() != ListStatus.CANCELED, "A lista está cancelada");
            if (list.getPublishedAt() != null) reason(request.command());
            var ids = new LinkedHashSet<>(request.chargeIds());
            if (ids.size() != request.chargeIds().size()) throw new AppException(HttpStatus.BAD_REQUEST, "Cada cobrança só pode aparecer uma vez");
            var rows = charges.findByIdInAndPeladaIdAndListIdOrderById(ids, id, listId);
            if (rows.size() != ids.size()) throw missing("Cobrança");
            var newComponents = components(request.components());
            BigDecimal adjustment = parse(request.adjustment());
            BigDecimal total = check(newComponents.stream().map(PriceComponent::getAmount).reduce(ZERO, BigDecimal::add).add(adjustment));
            require(total.signum() >= 0, "O valor devido não pode ser negativo");
            var pending = new HashSet<>(notices.chargeIdsWithStatus(ids, NoticeStatus.PENDING));
            var paid = reads.paid(rows);
            for (var c : rows) {
                active(c);
                require(!pending.contains(c.getId()), "Confira o pagamento informado por " + c.getPersonName() + " antes de ajustar");
                BigDecimal received = paid.getOrDefault(c.getId(), ZERO);
                require(received.compareTo(total) <= 0, c.getPersonName() + " já pagou " + display(received) + ", acima do novo valor");
            }
            var before = reads.detail(ctx, listId);
            for (var c : rows) {
                c.getComponents().clear(); c.getComponents().addAll(copyComponents(newComponents));
                c.setAdjustment(adjustment); c.setAmountDue(total);
            }
            charges.saveAll(rows);
            return new Change(before, reads.detail(ctx, listId), listId);
        });
    }
    public FinanceViews.Result cancelCharge(Long id, Long listId, Long chargeId, FinanceRequests.Command command, String email) {
        return tx.execute(id, email, command, "CHARGE_CANCEL", "CHARGE:" + chargeId, command, true, ctx -> {
            var list = reads.findList(id, listId); require(list.getStatus() != ListStatus.CANCELED, "A lista está cancelada");
            if (list.getPublishedAt() != null) reason(command);
            var c = reads.findCharge(id, listId, chargeId); active(c); noPending(c);
            require(reads.paid(c).signum() == 0 && !hasValidReceipts(List.of(c)), "Resolva os recebimentos antes de cancelar a cobrança");
            var before = reads.detail(ctx, listId); c.setAdjustment(check(c.getAdjustment().subtract(c.getAmountDue()))); c.setAmountDue(ZERO); c.setCanceled(true);
            return new Change(before, reads.detail(ctx, listId), listId);
        });
    }
    private FinancePerson person(Context ctx, FinanceRequests.Charge request) {
        int choices = (request.userId() != null ? 1 : 0) + (request.personId() != null ? 1 : 0)
                + (request.guestName() != null && !request.guestName().isBlank() ? 1 : 0);
        require(choices == 1, "Selecione um membro ou informe um convidado");
        if (request.personId() != null) {
            var p = people.findByIdAndPeladaId(request.personId(), ctx.pelada().getId()).orElseThrow(() -> missing("Pessoa"));
            if (p.getUserId() != null) require(peladas.existsByIdAndMembers_Id(ctx.pelada().getId(), p.getUserId()), "O jogador não é membro desta pelada");
            return p;
        }
        if (request.userId() != null) {
            require(peladas.existsByIdAndMembers_Id(ctx.pelada().getId(), request.userId()), "O jogador não é membro desta pelada");
            return people.findByPeladaIdAndUserId(ctx.pelada().getId(), request.userId()).orElseGet(() -> {
                var p = new FinancePerson(); p.setPeladaId(ctx.pelada().getId()); p.setUserId(request.userId());
                p.setName(users.findById(request.userId()).orElseThrow(() -> missing("Jogador")).getUsername()); return people.save(p);
            });
        }
        var p = new FinancePerson(); p.setPeladaId(ctx.pelada().getId()); p.setName(request.guestName().trim()); return people.save(p);
    }
    void validateQr(Long peladaId, Long qrId) { if (qrId != null) qrs.findByIdAndPeladaId(qrId, peladaId).orElseThrow(() -> missing("QR Code")); }
    void noPending(FinanceCharge c) { require(!notices.existsByChargeIdAndStatus(c.getId(), NoticeStatus.PENDING), "Confira o aviso pendente antes de alterar esta cobrança"); }
    static void active(FinanceCharge c) { require(!c.isCanceled(), "A cobrança está cancelada"); }
    static List<PriceComponent> components(List<FinanceRequests.Component> items) {
        List<PriceComponent> result = items.stream().map(c -> {
            BigDecimal amount = parse(c.amount()); require(amount.signum() >= 0, "Componentes não podem ser negativos");
            return new PriceComponent(c.name().trim(), amount);
        }).toList();
        check(result.stream().map(PriceComponent::getAmount).reduce(ZERO, BigDecimal::add));
        return result;
    }
    static List<PriceComponent> copyComponents(List<PriceComponent> items) {
        return items.stream().map(c -> new PriceComponent(c.getName(), c.getAmount())).toList();
    }
    private boolean componentsEqual(List<PriceComponent> saved, List<FinanceRequests.Component> incoming) {
        return FinanceReadService.componentViews(saved).equals(incoming.stream().map(c -> new FinanceViews.Component(c.name().trim(), c.amount())).toList());
    }
}
