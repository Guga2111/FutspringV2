package com.futspring.backend.service;

import com.futspring.backend.dto.PeladaResponseDTO;
import com.futspring.backend.entity.Pelada;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.helper.UserAuthenticationHelper;
import com.futspring.backend.repository.PeladaRepository;
import com.futspring.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeladaServiceImageTest {

    @Mock
    PeladaRepository peladaRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    FileUploadService fileUploadService;

    @Mock
    UserAuthenticationHelper userAuthHelper;

    PeladaService peladaService;

    User admin;
    User member;
    Pelada pelada;

    @BeforeEach
    void setUp() {
        peladaService = new PeladaService(peladaRepository, userRepository, fileUploadService, userAuthHelper);

        admin = User.builder().id(1L).email("admin@example.com").username("admin").password("hash").build();
        member = User.builder().id(2L).email("member@example.com").username("member").password("hash").build();

        pelada = Pelada.builder()
                .id(10L)
                .name("Pelada Test")
                .dayOfWeek("MONDAY")
                .timeOfDay("18:00")
                .duration(1.5f)
                .members(new HashSet<>())
                .admins(new HashSet<>())
                .build();
        pelada.getMembers().add(admin);
        pelada.getMembers().add(member);
        pelada.getAdmins().add(admin);
    }

    @Test
    void uploadPeladaImage_success() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileUploadService.uploadImage(any())).thenReturn("new-cover.jpg");

        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[100]);
        PeladaResponseDTO result = peladaService.uploadPeladaImage(10L, file, "admin@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getImage()).isEqualTo("new-cover.jpg");
        verify(fileUploadService).uploadImage(file);
    }

    @Test
    void uploadPeladaImage_nonAdminForbidden() {
        when(userAuthHelper.getAuthenticatedUser("member@example.com")).thenReturn(member);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));

        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[100]);

        assertThatThrownBy(() -> peladaService.uploadPeladaImage(10L, file, "member@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void uploadPeladaImage_peladaNotFound() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(99L)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", new byte[100]);

        assertThatThrownBy(() -> peladaService.uploadPeladaImage(99L, file, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void uploadPeladaImage_fileTooLarge() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(fileUploadService.uploadImage(any())).thenThrow(new AppException(HttpStatus.BAD_REQUEST, "File size exceeds 5MB limit"));

        byte[] bigFile = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "cover.jpg", "image/jpeg", bigFile);

        assertThatThrownBy(() -> peladaService.uploadPeladaImage(10L, file, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadPeladaImage_invalidMimeType() {
        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(fileUploadService.uploadImage(any())).thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, and WebP images are allowed"));

        MockMultipartFile file = new MockMultipartFile("file", "anim.gif", "image/gif", new byte[100]);

        assertThatThrownBy(() -> peladaService.uploadPeladaImage(10L, file, "admin@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadPeladaImage_deletesOldImage() {
        pelada.setImage("old-cover.jpg");

        when(userAuthHelper.getAuthenticatedUser("admin@example.com")).thenReturn(admin);
        when(peladaRepository.findById(10L)).thenReturn(Optional.of(pelada));
        when(peladaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileUploadService.uploadImage(any())).thenReturn("new-cover.png");

        MockMultipartFile file = new MockMultipartFile("file", "new-cover.png", "image/png", new byte[200]);
        peladaService.uploadPeladaImage(10L, file, "admin@example.com");

        verify(fileUploadService).deleteImage("old-cover.jpg");
    }
}
