package com.futspring.backend.service;

import com.futspring.backend.dto.CreatePeladaRequestDTO;
import com.futspring.backend.dto.PeladaDetailResponseDTO;
import com.futspring.backend.dto.PeladaResponseDTO;
import com.futspring.backend.dto.UserResponseDTO;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PeladaService {

    private final PeladaRepository peladaRepository;
    private final UserRepository userRepository;

    @Transactional
    public PeladaResponseDTO createPelada(CreatePeladaRequestDTO request, String currentUserEmail) {
        User user = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Pelada pelada = Pelada.builder()
                .name(request.getName())
                .dayOfWeek(request.getDayOfWeek())
                .timeOfDay(request.getTimeOfDay())
                .duration(request.getDuration())
                .address(request.getAddress())
                .reference(request.getReference())
                .autoCreateDailyEnabled(request.isAutoCreateDailyEnabled())
                .creator(user)
                .build();

        pelada.getMembers().add(user);
        pelada.getAdmins().add(user);

        Pelada saved = peladaRepository.save(pelada);
        return PeladaResponseDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PeladaResponseDTO> getMyPeladas(String currentUserEmail) {
        User user = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        return peladaRepository.findByMembersContaining(user).stream()
                .map(PeladaResponseDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PeladaDetailResponseDTO getPeladaDetail(Long id, String currentUserEmail) {
        User user = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Pelada pelada = peladaRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        if (!pelada.getMembers().contains(user)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Access denied: you are not a member of this pelada");
        }

        return PeladaDetailResponseDTO.from(pelada);
    }

    @Transactional
    public void addPlayer(Long peladaId, Long targetUserId, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can add players");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        if (pelada.getMembers().contains(target)) {
            throw new AppException(HttpStatus.CONFLICT, "User is already a member of this pelada");
        }

        pelada.getMembers().add(target);
        peladaRepository.save(pelada);
    }

    @Transactional
    public void removePlayer(Long peladaId, Long targetUserId, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can remove players");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        if (pelada.getCreator() != null && pelada.getCreator().getId().equals(targetUserId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Cannot remove the creator of the pelada");
        }

        pelada.getMembers().remove(target);
        pelada.getAdmins().remove(target);
        peladaRepository.save(pelada);
    }

    @Transactional
    public void setAdmin(Long peladaId, Long targetUserId, boolean isAdmin, String currentUserEmail) {
        User caller = userRepository.findByEmail(currentUserEmail)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));

        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        if (!pelada.getAdmins().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Only admins can change admin status");
        }

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        if (pelada.getCreator() != null && pelada.getCreator().getId().equals(targetUserId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Cannot change admin status of the creator");
        }

        if (isAdmin) {
            pelada.getAdmins().add(target);
        } else {
            pelada.getAdmins().remove(target);
        }
        peladaRepository.save(pelada);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> searchUsers(String query) {
        return userRepository.searchByUsernameOrEmail(query).stream()
                .limit(10)
                .map(UserResponseDTO::from)
                .collect(Collectors.toList());
    }
}
