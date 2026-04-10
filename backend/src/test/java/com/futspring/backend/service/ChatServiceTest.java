package com.futspring.backend.service;

import com.futspring.backend.dto.MessageDTO;
import com.futspring.backend.entity.Message;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.MessageRepository;
import com.futspring.backend.repository.PeladaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    MessageRepository messageRepository;
    @Mock
    PeladaRepository peladaRepository;
    @Mock
    UserAuthenticationHelper userAuthHelper;

    ChatService chatService;

    User admin;
    User member;
    User outsider;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(messageRepository, peladaRepository, userAuthHelper);

        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").build();
        member = User.builder().id(2L).email("member@example.com").username("member").password("hash").build();
        outsider = User.builder().id(3L).email("out@example.com").username("out").password("hash").build();

        pelada = Pelada.builder()
                .id(10L)
                .name("Pelada")
                .dayOfWeek("FRIDAY")
                .timeOfDay("18:00")
                .duration(2f)
                .members(new HashSet<>(Set.of(admin, member)))
                .admins(new HashSet<>(Set.of(admin)))
                .build();
    }

    // --- saveAndBroadcast ---

    @Test
    void saveAndBroadcast_success_returnsDTO() {
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 1L);
            ReflectionTestUtils.setField(m, "sentAt", LocalDateTime.now());
            return m;
        });

        MessageDTO result = chatService.saveAndBroadcast(10L, "member@example.com", "Hello!");

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("Hello!");
        assertThat(result.getSender().getUsername()).isEqualTo("member");
    }

    @Test
    void saveAndBroadcast_peladaNotFound_throwsNotFound() {
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.saveAndBroadcast(999L, "member@example.com", "Hello!"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void saveAndBroadcast_senderNotFound_throwsUnauthorized() {
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> chatService.saveAndBroadcast(10L, "ghost@example.com", "Hello!"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void saveAndBroadcast_notMember_throwsForbidden() {
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);

        assertThatThrownBy(() -> chatService.saveAndBroadcast(10L, "out@example.com", "Hello!"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void saveAndBroadcast_blankContent_throwsBadRequest() {
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);

        assertThatThrownBy(() -> chatService.saveAndBroadcast(10L, "member@example.com", "   "))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void saveAndBroadcast_nullContent_throwsBadRequest() {
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);

        assertThatThrownBy(() -> chatService.saveAndBroadcast(10L, "member@example.com", null))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void saveAndBroadcast_tooLongContent_throwsBadRequest() {
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);

        String longContent = "x".repeat(501);
        assertThatThrownBy(() -> chatService.saveAndBroadcast(10L, "member@example.com", longContent))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void saveAndBroadcast_exactly500Chars_succeeds() {
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 1L);
            ReflectionTestUtils.setField(m, "sentAt", LocalDateTime.now());
            return m;
        });

        String content500 = "x".repeat(500);
        MessageDTO result = chatService.saveAndBroadcast(10L, "member@example.com", content500);

        assertThat(result.getContent()).hasSize(500);
    }

    // --- getHistory ---

    @Test
    void getHistory_success_returnsChronologicalOrder() {
        LocalDateTime earlier = LocalDateTime.of(2024, 1, 1, 10, 0);
        LocalDateTime later = LocalDateTime.of(2024, 1, 1, 11, 0);

        Message m1 = Message.builder().pelada(pelada).sender(member).content("First").build();
        Message m2 = Message.builder().pelada(pelada).sender(member).content("Second").build();
        ReflectionTestUtils.setField(m1, "id", 1L);
        ReflectionTestUtils.setField(m1, "sentAt", earlier);
        ReflectionTestUtils.setField(m2, "id", 2L);
        ReflectionTestUtils.setField(m2, "sentAt", later);

        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        // Repository returns desc order (latest first), service reverses it
        when(messageRepository.findByPeladaOrderBySentAtDesc(eq(pelada), any(Pageable.class)))
                .thenReturn(List.of(m2, m1));

        List<MessageDTO> result = chatService.getHistory(10L, "member@example.com", 0, 50);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getContent()).isEqualTo("First");
        assertThat(result.get(1).getContent()).isEqualTo("Second");
    }

    @Test
    void getHistory_peladaNotFound_throwsNotFound() {
        when(peladaRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getHistory(999L, "member@example.com", 0, 50))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getHistory_callerNotFound_throwsUnauthorized() {
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("ghost@example.com")).thenThrow(new AppException(HttpStatus.NOT_FOUND, "User not found"));

        assertThatThrownBy(() -> chatService.getHistory(10L, "ghost@example.com", 0, 50))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getHistory_notMember_throwsForbidden() {
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("out@example.com")).thenReturn(outsider);

        assertThatThrownBy(() -> chatService.getHistory(10L, "out@example.com", 0, 50))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void getHistory_pagination_passedToRepository() {
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(messageRepository.findByPeladaOrderBySentAtDesc(eq(pelada), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        chatService.getHistory(10L, "member@example.com", 2, 10);

        verify(messageRepository).findByPeladaOrderBySentAtDesc(eq(pelada),
                argThat(p -> p.getPageNumber() == 2 && p.getPageSize() == 10));
    }

    @Test
    void getHistory_emptyPelada_returnsEmptyList() {
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(messageRepository.findByPeladaOrderBySentAtDesc(eq(pelada), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        List<MessageDTO> result = chatService.getHistory(10L, "member@example.com", 0, 50);

        assertThat(result).isEmpty();
    }
}
