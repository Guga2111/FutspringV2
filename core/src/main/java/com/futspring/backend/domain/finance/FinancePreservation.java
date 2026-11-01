package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceAccess.require;

/** Narrow internal integration boundary; other domains never edit financial tables themselves. */
@Service
@RequiredArgsConstructor
@Transactional
public class FinancePreservation {
    private final FinanceAccountRepository accounts;
    private final FinanceListRepository lists;
    private final FinanceChargeRepository charges;
    private final FinanceMovementRepository movements;
    private final FinanceTemplateRepository templates;
    private final FinancePersonRepository people;
    private final FinanceManagerRepository managers;
    private final FinanceAuditRepository audits;
    private final FinanceQrRepository qrs;
    private final FinanceFiles files;
    private final FinanceAccess access;

    public void removeMembers(Pelada pelada, Collection<User> targets, User actor) {
        var removed = managers.findByPeladaId(pelada.getId()).stream()
                .filter(m -> targets.stream().anyMatch(u -> u.getId().equals(m.getUserId()))).toList();
        if (removed.isEmpty()) return;
        managers.deleteAll(removed);
        record(pelada.getId(), actor, "MANAGERS_REVOKED", "Remoção de membro: " + removed.stream().map(FinanceManager::getUserId).toList());
    }
    public void detachDaily(Daily daily, User actor) {
        var linkedLists = lists.findByDailyId(daily.getId()); var linkedCash = movements.findByDailyId(daily.getId());
        if (linkedLists.isEmpty() && linkedCash.isEmpty()) return;
        linkedLists.forEach(l -> l.setDailyId(null)); linkedCash.forEach(m -> m.setDailyId(null));
        record(daily.getPelada().getId(), actor, "DAILY_DETACHED", "Diária excluída: " + daily.getId() + " · " + daily.getDailyDate());
    }
    public void deleteDraftOnlyPelada(Long id) {
        require(!lists.existsByPeladaIdAndPublishedAtIsNotNull(id) && !movements.existsByPeladaId(id),
                "Esta pelada tem histórico financeiro e não pode ser excluída");
        var draftLists = lists.findByPeladaId(id);
        // Drafts cannot have notices or receipts. Components are removed by the owning JPA collection.
        for (var list : draftLists) charges.deleteAll(charges.findByListIdOrderById(list.getId()));
        charges.flush(); lists.deleteAll(draftLists); lists.flush();
        templates.deleteAll(templates.findByPeladaIdOrderById(id)); templates.flush();
        people.deleteAll(people.findByPeladaId(id)); managers.deleteAll(managers.findByPeladaId(id));
        audits.deleteAll(audits.findByPeladaId(id)); accounts.findByPeladaId(id).ifPresent(accounts::delete); accounts.flush();
        for (var qr : qrs.findByPeladaId(id)) { files.deleteAfterCommit(qr.getFileKey(), true); qrs.delete(qr); }
    }
    private void record(Long id, User actor, String operation, String description) {
        accounts.findByPeladaId(id).ifPresent(a -> a.setRevision(a.getRevision() + 1));
        var audit = new FinanceAudit(); audit.setPeladaId(id); audit.setActorId(actor.getId()); audit.setActorName(actor.getUsername());
        audit.setCreatedAt(access.now()); audit.setOperation(FinanceTypes.AuditOperation.valueOf(operation)); audit.setTarget(operation); audit.setReason(description); audits.save(audit);
    }
}
