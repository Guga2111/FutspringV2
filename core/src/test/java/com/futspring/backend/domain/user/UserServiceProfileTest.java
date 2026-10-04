package com.futspring.backend.domain.user;

import com.futspring.backend.domain.file.FileUploadService;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.domain.user.dto.ProfileDTO;
import com.futspring.backend.domain.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceProfileTest {

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
                .email("alice@example.com")
                .username("alice")
                .password("hash")
                .stars(3)
                .build();
    }

    // --- getProfile ---

    @Test
    void getProfile_existingUser_returnsProfileDTO() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ProfileDTO result = userService.getProfile(1L, "alice@example.com");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    void getProfile_otherViewer_hidesEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ProfileDTO result = userService.getProfile(1L, "bob@example.com");

        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getEmail()).isNull();
    }

    @Test
    void getProfile_userNotFound_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(99L, "alice@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // --- updateProfile ---

    @Test
    void updateProfile_success_updatesAllFields() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("newname", 1L)).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("newname");
        req.setPosition("GOALKEEPER");
        req.setStars(5);

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");

        assertThat(result.getUsername()).isEqualTo("newname");
        assertThat(result.getPosition()).isEqualTo("GOALKEEPER");
        assertThat(result.getStars()).isEqualTo(5);
    }

    @Test
    void updateProfile_userNotFound_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateProfileRequest req = new UpdateProfileRequest();
        assertThatThrownBy(() -> userService.updateProfile(99L, req, "alice@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateProfile_callerIsNotOwner_throwsForbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileRequest req = new UpdateProfileRequest();
        assertThatThrownBy(() -> userService.updateProfile(1L, req, "other@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updateProfile_usernameAlreadyTakenByOtherUser_throwsConflict() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("taken", 1L)).thenReturn(true);

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("taken");

        assertThatThrownBy(() -> userService.updateProfile(1L, req, "alice@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateProfile_usernameAlreadyTakenBySameUser_updatesSuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername("alice");

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    void updateProfile_nullUsername_doesNotChangeUsername() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setUsername(null);

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    void updateProfile_validPosition_setsPosition() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setPosition("FORWARD");

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");
        assertThat(result.getPosition()).isEqualTo("FORWARD");
    }

    @Test
    void updateProfile_invalidPosition_rejectedByRequestValidation() {
        // The allowed positions are a constraint on UpdateProfileRequest (400 from @Valid in the controller)
        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setPosition("INVALID_POS");

        try (jakarta.validation.ValidatorFactory factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(req))
                    .extracting(v -> v.getPropertyPath().toString())
                    .containsExactly("position");
        }
    }

    @Test
    void updateProfile_emptyStringPosition_clearsPosition() {
        user.setPosition("FORWARD");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setPosition("");

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");
        assertThat(result.getPosition()).isNull();
    }

    @Test
    void updateProfile_nullPosition_doesNotChangePosition() {
        user.setPosition("MIDFIELDER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setPosition(null);

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");
        assertThat(result.getPosition()).isEqualTo("MIDFIELDER");
    }

    @Test
    void updateProfile_starsUpdate_persisted() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest();
        req.setStars(5);

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");
        assertThat(result.getStars()).isEqualTo(5);
    }
}
