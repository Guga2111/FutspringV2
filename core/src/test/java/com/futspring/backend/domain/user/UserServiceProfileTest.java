package com.futspring.backend.domain.user;

import com.futspring.backend.domain.pelada.PeladaPlayerStarsRepository;
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

    @Mock
    PeladaPlayerStarsRepository playerStarsRepository;

    UserService userService;

    User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, fileUploadService, playerStarsRepository);

        user = User.builder()
                .id(1L)
                .email("alice@example.com")
                .username("alice")
                .password("hash")
                .build();
    }

    // --- getProfile ---

    @Test
    void getProfile_existingUser_returnsProfileDTO() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ProfileDTO result = userService.getProfile(1L, "alice@example.com");

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("alice@example.com");
        assertThat(result.username()).isEqualTo("alice");
    }

    @Test
    void getProfile_otherViewer_hidesEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        ProfileDTO result = userService.getProfile(1L, "bob@example.com");

        assertThat(result.username()).isEqualTo("alice");
        assertThat(result.email()).isNull();
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

        UpdateProfileRequest req = new UpdateProfileRequest("newname", "GOALKEEPER");

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");

        assertThat(result.username()).isEqualTo("newname");
        assertThat(result.position()).isEqualTo("GOALKEEPER");
    }

    @Test
    void updateProfile_userNotFound_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateProfileRequest req = new UpdateProfileRequest(null, null);
        assertThatThrownBy(() -> userService.updateProfile(99L, req, "alice@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateProfile_callerIsNotOwner_throwsForbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UpdateProfileRequest req = new UpdateProfileRequest(null, null);
        assertThatThrownBy(() -> userService.updateProfile(1L, req, "other@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void updateProfile_usernameAlreadyTakenByOtherUser_throwsConflict() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameIgnoreCaseAndIdNot("taken", 1L)).thenReturn(true);

        UpdateProfileRequest req = new UpdateProfileRequest("taken", null);

        assertThatThrownBy(() -> userService.updateProfile(1L, req, "alice@example.com"))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void updateProfile_usernameAlreadyTakenBySameUser_updatesSuccessfully() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest("alice", null);

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");
        assertThat(result.username()).isEqualTo("alice");
    }

    @Test
    void updateProfile_nullUsername_doesNotChangeUsername() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest(null, null);

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");
        assertThat(result.username()).isEqualTo("alice");
    }

    @Test
    void updateProfile_validPosition_setsPosition() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest(null, "FORWARD");

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");
        assertThat(result.position()).isEqualTo("FORWARD");
    }

    @Test
    void updateProfile_invalidPosition_rejectedByRequestValidation() {
        // The allowed positions are a constraint on UpdateProfileRequest (400 from @Valid in the controller)
        UpdateProfileRequest req = new UpdateProfileRequest(null, "INVALID_POS");

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

        UpdateProfileRequest req = new UpdateProfileRequest(null, "");

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");
        assertThat(result.position()).isNull();
    }

    @Test
    void updateProfile_nullPosition_doesNotChangePosition() {
        user.setPosition("MIDFIELDER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateProfileRequest req = new UpdateProfileRequest(null, null);

        ProfileDTO result = userService.updateProfile(1L, req, "alice@example.com");
        assertThat(result.position()).isEqualTo("MIDFIELDER");
    }

    @Test
    void getProfile_starsAreTheRoundedAverageOfThePeladas() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(playerStarsRepository.averageStarsByUser(1L)).thenReturn(3.5);

        ProfileDTO result = userService.getProfile(1L, "bob@example.com");

        assertThat(result.stars()).isEqualTo(4);
    }

    @Test
    void getProfile_withoutPeladas_hasNoStars() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(playerStarsRepository.averageStarsByUser(1L)).thenReturn(null);

        ProfileDTO result = userService.getProfile(1L, "bob@example.com");

        assertThat(result.stars()).isNull();
    }
}
