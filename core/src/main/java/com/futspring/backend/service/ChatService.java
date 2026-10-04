package com.futspring.backend.service;

import com.futspring.backend.dto.MessageDTO;
import com.futspring.backend.entity.Message;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.PeladaAccessHelper;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.MessageRepository;
import com.futspring.backend.repository.PeladaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatService {

    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_CONTENT_LENGTH = 500;

    private final MessageRepository messageRepository;
    private final PeladaRepository peladaRepository;
    private final UserAuthenticationHelper userAuthHelper;
    private final PeladaAccessHelper accessHelper;

    @Transactional
    public MessageDTO saveAndBroadcast(Long peladaId, String senderEmail, String content) {
        Pelada pelada = findPelada(peladaId);
        User sender = userAuthHelper.getAuthenticatedUser(senderEmail);
        accessHelper.requireMember(pelada, sender);

        if (content == null || content.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "A mensagem não pode ser vazia");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new AppException(HttpStatus.BAD_REQUEST, "A mensagem deve ter no máximo " + MAX_CONTENT_LENGTH + " caracteres");
        }

        Message message = Message.builder()
                .pelada(pelada)
                .sender(sender)
                .content(content)
                .build();
        return MessageDTO.from(messageRepository.save(message));
    }

    /** Page of messages, newest page first, returned oldest first. Size is clamped to 1..100. */
    @Transactional(readOnly = true)
    public List<MessageDTO> getHistory(Long peladaId, String callerEmail, int page, int size) {
        Pelada pelada = findPelada(peladaId);
        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);
        accessHelper.requireMember(pelada, caller);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<MessageDTO> result = new ArrayList<>(messageRepository
                .findByPeladaOrderBySentAtDesc(pelada, PageRequest.of(safePage, safeSize)).stream()
                .map(MessageDTO::from)
                .toList());
        Collections.reverse(result);
        return result;
    }

    private Pelada findPelada(Long id) {
        return peladaRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada não encontrada"));
    }
}
