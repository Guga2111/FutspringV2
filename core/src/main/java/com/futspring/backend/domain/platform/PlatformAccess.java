package com.futspring.backend.domain.platform;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** The platform admin's panel: called first by every platform service method. */
@Component
@RequiredArgsConstructor
public class PlatformAccess {

    private final UserAuthenticationHelper userAuthHelper;

    /** The caller when they operate the platform; anyone else gets the same 404 as an unknown route. */
    public User requirePlatformAdmin(String email) {
        User caller = userAuthHelper.getAuthenticatedUser(email);
        if (!caller.isPlatformAdmin() || caller.isDeleted()) {
            throw new AppException(HttpStatus.NOT_FOUND, "Recurso não encontrado");
        }
        return caller;
    }
}
