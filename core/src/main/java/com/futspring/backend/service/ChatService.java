package com.futspring.backend.service;

import com.futspring.backend.dto.MessageDTO;
import com.futspring.backend.entity.Message;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.MessageRepository;
import com.futspring.backend.repository.PeladaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;
    private final PeladaRepository peladaRepository;
    private final UserAuthenticationHelper userAuthHelper;

    @Transactional
    public MessageDTO saveAndBroadcast(Long peladaId, String senderEmail, String content) {
        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        User sender = userAuthHelper.getAuthenticatedUser(senderEmail);

        if (!pelada.getMembers().contains(sender)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not a member of this pelada");
        }

        if (content == null || content.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Content must not be blank");
        }

        if (content.length() > 500) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Content must not exceed 500 characters");
        }

        Message message = Message.builder()
                .pelada(pelada)
                .sender(sender)
                .content(content)
                .build();

        Message saved = messageRepository.save(message);
        return MessageDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MessageDTO> getHistory(Long peladaId, String callerEmail, int page, int size) {
        Pelada pelada = peladaRepository.findById(peladaId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Pelada not found"));

        User caller = userAuthHelper.getAuthenticatedUser(callerEmail);

        if (!pelada.getMembers().contains(caller)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Not a member of this pelada");
        }

        List<Message> messages = messageRepository.findByPeladaOrderBySentAtDesc(
                pelada, PageRequest.of(page, size));

        List<MessageDTO> result = messages.stream()
                .map(MessageDTO::from)
                .collect(Collectors.toList());

        Collections.reverse(result);
        return result;
    }
}
