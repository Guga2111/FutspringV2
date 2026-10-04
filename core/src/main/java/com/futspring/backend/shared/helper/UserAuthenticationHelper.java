package com.futspring.backend.shared.helper;

import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserAuthenticationHelper {

    private final UserRepository userRepository;

    public User getAuthenticatedUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }
}
