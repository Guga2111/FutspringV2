package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.daily.Daily;
import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRef;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.futspring.backend.domain.finance.FinanceAccess.require;
import com.futspring.backend.domain.finance.account.FinanceAccountRepository;
import com.futspring.backend.domain.finance.account.FinanceAudit;
import com.futspring.backend.domain.finance.account.FinanceAuditRepository;
import com.futspring.backend.domain.finance.account.FinanceManager;
import com.futspring.backend.domain.finance.account.FinanceManagerRepository;
import com.futspring.backend.domain.finance.account.FinanceQrRepository;
import com.futspring.backend.domain.finance.collection.FinanceChargeRepository;
import com.futspring.backend.domain.finance.collection.FinanceListRepository;
import com.futspring.backend.domain.finance.collection.FinancePersonRepository;
import com.futspring.backend.domain.finance.collection.FinanceTemplateRepository;
import com.futspring.backend.domain.finance.ledger.FinanceMovementRepository;

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

    /** The creator starts as the only responsável financeiro of a new pelada (already a member of it). */
    public void appointCreator(Pelada pelada) {
        var manager = new FinanceManager();
        manager.setPeladaId(pelada.getId());
        manager.setUserId(pelada.getCreator().getId());
        // finance_managers references the membership row, which is only written on flush
        managers.flush();
        managers.save(manager);
    }
    /** Revokes the removed members' delegation; removing every responsável at once is refused (409), so the money always has someone. */
    public void removeMembers(Pelada pelada, Collection<User> targets, User actor) {
        var current = managers.findByPeladaId(pelada.getId());
        var removed = current.stream()
                .filter(m -> targets.stream().anyMatch(u -> u.getId().equals(m.getUserId()))).toList();
        if (removed.isEmpty()) return;
        if (removed.size() == current.size()) {
            var names = targets.stream().filter(u -> removed.stream().anyMatch(m -> m.getUserId().equals(u.getId())))
                    .map(User::getUsername).toList();
            throw new AppException(HttpStatus.CONFLICT, names.size() == 1
                    ? names.getFirst() + " é o único responsável financeiro. Designe outro antes de removê-lo."
                    : "Designe outro responsável financeiro antes de remover " + String.join(", ", names) + ".");
        }
        managers.deleteAll(removed);
        record(pelada.getId(), actor, "MANAGERS_REVOKED", "Remoção de membro: " + removed.stream().map(FinanceManager::getUserId).toList());
    }
    /** Peladas where the user is the only responsável financeiro: their account deletion is blocked there. */
    public List<PeladaRef> soleManagedPeladas(Long userId) {
        return managers.findSoleManagedBy(userId);
    }
    /**
     * Account deletion, with the user's peladas already locked and {@link #soleManagedPeladas} empty: they stop being
     * a responsável everywhere (audited per pelada) and their financial identity is renamed. Charges, receipts,
     * notices, cash and audit stay. A constant number of reads whatever the number of peladas.
     */
    public void forgetAccount(User user, String anonymousName) {
        var managed = managers.findPeladaIdsByUserId(user.getId());
        if (!managed.isEmpty()) {
            managers.deleteByUserId(user.getId());
            accounts.incrementRevisions(managed);
            var now = access.now();
            for (Long peladaId : managed) {
                var audit = new FinanceAudit(); audit.setPeladaId(peladaId); audit.setActorId(user.getId()); audit.setActorName(anonymousName);
                audit.setCreatedAt(now); audit.setOperation(FinanceTypes.AuditOperation.MANAGERS_REVOKED); audit.setTarget("MANAGERS_REVOKED");
                audit.setReason("Conta excluída: deixou de ser responsável financeiro"); audits.save(audit);
            }
        }
        people.renameUser(user.getId(), anonymousName);
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
