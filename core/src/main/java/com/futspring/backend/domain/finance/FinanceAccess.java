package com.futspring.backend.domain.finance;

import com.futspring.backend.domain.pelada.*;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.*;
import com.futspring.backend.domain.finance.account.FinanceAccount;
import com.futspring.backend.domain.finance.account.FinanceAccountRepository;
import com.futspring.backend.domain.finance.account.FinanceManagerRepository;

@Component
@RequiredArgsConstructor
public class FinanceAccess {
    private final PeladaRepository peladas;
    private final FinanceAccountRepository accounts;
    private final FinanceManagerRepository managers;
    private final UserAuthenticationHelper authentication;
    private final PeladaAccessHelper access;
    private final Clock clock;

    record Context(Pelada pelada, User caller, FinanceAccount account, boolean manage, boolean appoint) {
        boolean viewCash() { return manage || account.getVisibility() == FinanceTypes.Visibility.TRANSPARENT; }
        void requireManager() {
            if (!manage) throw new AppException(HttpStatus.FORBIDDEN, "Apenas responsáveis financeiros podem fazer isso");
        }
    }
    Context read(Long id, String email) { return load(id, email, false); }
    Context write(Long id, String email) { return load(id, email, true); }
    private Context load(Long id, String email, boolean lock) {
        User caller = authentication.getAuthenticatedUser(email);
        Pelada pelada = (lock ? peladas.findByIdForUpdate(id) : peladas.findById(id))
                .orElseThrow(() -> missing("Pelada"));
        access.requireMember(pelada, caller);
        // only designated responsáveis manage the money; admins and the creator may appoint them, and so may the responsáveis
        boolean manage = managers.existsByPeladaIdAndUserId(id, caller.getId());
        boolean appoint = manage || access.isCreator(pelada, caller) || access.isAdmin(pelada, caller);
        FinanceAccount account = accounts.findByPeladaId(id).orElseGet(() -> {
            FinanceAccount initial = new FinanceAccount();
            initial.setPeladaId(id);
            return initial;
        });
        return new Context(pelada, caller, account, manage, appoint);
    }
    Context refresh(Context c) {
        boolean manage = managers.existsByPeladaIdAndUserId(c.pelada().getId(), c.caller().getId());
        return new Context(c.pelada(), c.caller(), c.account(), manage,
                manage || access.isCreator(c.pelada(), c.caller()) || access.isAdmin(c.pelada(), c.caller()));
    }
    LocalDate today(Context context) { return LocalDate.ofInstant(clock.instant(), ZoneId.of(context.pelada().getTimeZone())); }
    Instant now() { return clock.instant(); }
    static AppException missing(String subject) { return new AppException(HttpStatus.NOT_FOUND, subject + " não encontrado(a)"); }
    static void require(boolean condition, String message) {
        if (!condition) throw new AppException(HttpStatus.CONFLICT, message);
    }
}
