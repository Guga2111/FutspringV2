package com.futspring.backend.helper;

import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.PeladaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

// Relationship checks with exists queries, so the members/admins collections are never loaded just to call contains
@Component
@RequiredArgsConstructor
public class PeladaAccessHelper {

    private final PeladaRepository peladaRepository;

    public boolean isMember(Pelada pelada, User user) {
        return peladaRepository.existsByIdAndMembers_Id(pelada.getId(), user.getId());
    }

    public boolean isAdmin(Pelada pelada, User user) {
        return peladaRepository.existsByIdAndAdmins_Id(pelada.getId(), user.getId());
    }

    public boolean isCreator(Pelada pelada, User user) {
        return pelada.getCreator() != null && pelada.getCreator().getId().equals(user.getId());
    }

    public void requireMember(Pelada pelada, User user) {
        if (!isMember(pelada, user)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Acesso negado: você não é membro desta pelada");
        }
    }

    public void requireAdmin(Pelada pelada, User user) {
        if (!isAdmin(pelada, user)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Apenas administradores da pelada podem fazer isso");
        }
    }
}
