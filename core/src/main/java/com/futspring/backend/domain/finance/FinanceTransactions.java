package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.finance.dto.FinanceRequests.Command;
import com.futspring.backend.domain.finance.dto.FinanceViews;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Function;
import static com.futspring.backend.domain.finance.FinanceAccess.*;

/** Called inside a service transaction. One pelada lock owns authorization, revision, ledger and audit. */
@Component
@RequiredArgsConstructor
public class FinanceTransactions {
    private final FinanceAccess access;
    private final FinanceAccountRepository accounts;
    private final FinanceTemplateRepository templates;
    private final FinanceAuditRepository audits;
    private final FinanceReadService reads;
    private final JsonMapper json;

    record Change(Object before, Object after, Long listId) {}
    FinanceViews.Result execute(Long id, String email, Command command, String operation, String target,
                               Object payload, boolean manager, Function<Context, Change> action) {
        Context ctx = access.write(id, email);
        if (manager) ctx.requireManager();
        String hash = hash(json.writeValueAsString(List.of(ctx.caller().getId(), operation, target, payload)));
        var previous = audits.findByPeladaIdAndRequestKey(id, command.requestKey());
        if (previous.isPresent()) {
            require(hash.equals(previous.get().getRequestHash()), "A chave de reenvio já foi usada em outra operação");
            return result(ctx, previous.get().getResultListId());
        }
        require(ctx.account().getRevision() == command.revision(), "O financeiro mudou. Atualize a tela e tente novamente");
        if (ctx.account().getId() == null) {
            accounts.save(ctx.account());
            // The same defaults shown on a pristine read become editable, durable templates on first write.
            seed(ctx, "Mensalidade", FinanceTypes.Classification.MONTHLY);
            seed(ctx, "Diária", FinanceTypes.Classification.DAILY);
            seed(ctx, "Evento", FinanceTypes.Classification.OTHER);
        }
        Change change = action.apply(ctx);
        ctx.account().setRevision(ctx.account().getRevision() + 1);
        FinanceAudit audit = new FinanceAudit();
        audit.setPeladaId(id); audit.setActorId(ctx.caller().getId()); audit.setActorName(ctx.caller().getUsername());
        audit.setOperation(FinanceTypes.AuditOperation.valueOf(operation));
        String auditTarget = target;
        if (target.endsWith(":null")) {
            if (change.after() instanceof FinanceViews.Detail detail) auditTarget = "LIST:" + detail.collection().id();
            if (change.after() instanceof FinanceViews.Template template) auditTarget = "TEMPLATE:" + template.id();
        }
        audit.setTarget(auditTarget); audit.setReason(command.reason()); audit.setCreatedAt(access.now());
        audit.setBeforeData(change.before() == null ? null : json.writeValueAsString(change.before()));
        audit.setAfterData(change.after() == null ? null : json.writeValueAsString(change.after()));
        audit.setRequestKey(command.requestKey()); audit.setRequestHash(hash); audit.setResultListId(change.listId());
        audits.save(audit);
        return result(ctx, change.listId());
    }
    private FinanceViews.Result result(Context ctx, Long listId) {
        return new FinanceViews.Result(reads.overview(ctx, 0, 50), listId == null ? null : reads.detail(ctx, listId));
    }
    private void seed(Context ctx, String name, FinanceTypes.Classification kind) {
        FinanceTemplate template = new FinanceTemplate();
        template.setPeladaId(ctx.pelada().getId()); template.setName(name); template.setClassification(kind);
        template.getComponents().add(new PriceComponent("Campo", FinanceMoney.ZERO));
        templates.save(template);
    }
    static void reason(Command command) {
        if (command.reason() == null || command.reason().isBlank()) throw new AppException(HttpStatus.BAD_REQUEST, "Informe o motivo da alteração");
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
