package com.futspring.backend.service;

import com.futspring.backend.dto.ProfileDTO;
import com.futspring.backend.entity.User;
import com.futspring.backend.exception.AppException;
import com.futspring.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    FileUploadService fileUploadService;

    UserService userService;

    User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, fileUploadService);

        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .username("tester")
                .password("hash")
                .build();
    }

    // --- uploadUserImage ---

    @Test
    void uploadUserImage_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileUploadService.uploadImage(any())).thenReturn("photo.jpg");

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[100]);
        ProfileDTO result = userService.uploadUserImage(1L, file, "test@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getImage()).isEqualTo("photo.jpg");
        verify(fileUploadService).uploadImage(file);
    }

    @Test
    void uploadUserImage_unauthorized() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[100]);

        assertThatThrownBy(() -> userService.uploadUserImage(1L, file, "other@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void uploadUserImage_userNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[100]);

        assertThatThrownBy(() -> userService.uploadUserImage(99L, file, "test@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void uploadUserImage_fileTooLarge() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileUploadService.uploadImage(any())).thenThrow(new AppException(HttpStatus.BAD_REQUEST, "File size exceeds 5MB limit"));

        byte[] bigFile = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", bigFile);

        assertThatThrownBy(() -> userService.uploadUserImage(1L, file, "test@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadUserImage_invalidMimeType() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileUploadService.uploadImage(any())).thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, and WebP images are allowed"));

        MockMultipartFile file = new MockMultipartFile("file", "script.txt", "text/plain", new byte[100]);

        assertThatThrownBy(() -> userService.uploadUserImage(1L, file, "test@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadUserImage_deletesOldFile() {
        user.setImage("old-avatar.jpg");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileUploadService.uploadImage(any())).thenReturn("new.jpg");

        MockMultipartFile file = new MockMultipartFile("file", "new.jpg", "image/jpeg", new byte[100]);
        userService.uploadUserImage(1L, file, "test@example.com");

        verify(fileUploadService).deleteImage("old-avatar.jpg");
    }

    // --- uploadBackgroundImage ---

    @Test
    void uploadBackgroundImage_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileUploadService.uploadImage(any())).thenReturn("bg.png");

        MockMultipartFile file = new MockMultipartFile("file", "bg.png", "image/png", new byte[200]);
        ProfileDTO result = userService.uploadBackgroundImage(1L, file, "test@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getBackgroundImage()).isEqualTo("bg.png");
        verify(fileUploadService).uploadImage(file);
    }

    @Test
    void uploadBackgroundImage_unauthorized() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "bg.png", "image/png", new byte[200]);

        assertThatThrownBy(() -> userService.uploadBackgroundImage(1L, file, "other@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void uploadBackgroundImage_userNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile("file", "bg.png", "image/png", new byte[200]);

        assertThatThrownBy(() -> userService.uploadBackgroundImage(99L, file, "test@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void uploadBackgroundImage_fileTooLarge() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileUploadService.uploadImage(any())).thenThrow(new AppException(HttpStatus.BAD_REQUEST, "File size exceeds 5MB limit"));

        byte[] bigFile = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "bg.png", "image/png", bigFile);

        assertThatThrownBy(() -> userService.uploadBackgroundImage(1L, file, "test@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadBackgroundImage_invalidMimeType() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileUploadService.uploadImage(any())).thenThrow(new AppException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, and WebP images are allowed"));

        MockMultipartFile file = new MockMultipartFile("file", "script.txt", "text/plain", new byte[100]);

        assertThatThrownBy(() -> userService.uploadBackgroundImage(1L, file, "test@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void uploadBackgroundImage_deletesOldFile() {
        user.setBackgroundImage("old-bg.jpg");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(fileUploadService.uploadImage(any())).thenReturn("new-bg.png");

        MockMultipartFile file = new MockMultipartFile("file", "new-bg.png", "image/png", new byte[200]);
        userService.uploadBackgroundImage(1L, file, "test@example.com");

        verify(fileUploadService).deleteImage("old-bg.jpg");
    }
}
