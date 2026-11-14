package com.futspring.backend.domain.tour;

import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TourControllerTest extends BaseIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserTourRepository tourRepository;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH).email("tour@test.com").username("tour").password("hash").build());
        other = userRepository.save(User.builder().emailVerifiedAt(java.time.Instant.EPOCH).email("tourother@test.com").username("tourother").password("hash").build());
    }

    private String auth(User u) {
        return bearerToken(u.getId(), u.getEmail());
    }

    @Test
    void newUserHasSeenNoTour() throws Exception {
        mockMvc.perform(get("/api/v1/tours").header("Authorization", auth(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seen", empty()));
    }

    @Test
    void markSeenIsIdempotentAndOnlyForTheCaller() throws Exception {
        mockMvc.perform(post("/api/v1/tours/FINANCE_MANAGER/seen").header("Authorization", auth(user)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/tours/FINANCE_MANAGER/seen").header("Authorization", auth(user)))
                .andExpect(status().isNoContent());

        assertThat(tourRepository.findByIdUserId(user.getId())).hasSize(1);
        mockMvc.perform(get("/api/v1/tours").header("Authorization", auth(user)))
                .andExpect(jsonPath("$.seen", contains("FINANCE_MANAGER")));
        mockMvc.perform(get("/api/v1/tours").header("Authorization", auth(other)))
                .andExpect(jsonPath("$.seen", empty()));
    }

    @Test
    void toursAreListedInEnumOrder() throws Exception {
        mockMvc.perform(post("/api/v1/tours/FINANCE_MEMBER/seen").header("Authorization", auth(user)));
        mockMvc.perform(post("/api/v1/tours/FINANCE_MANAGER/seen").header("Authorization", auth(user)));

        mockMvc.perform(get("/api/v1/tours").header("Authorization", auth(user)))
                .andExpect(jsonPath("$.seen", contains("FINANCE_MANAGER", "FINANCE_MEMBER")));
    }

    @Test
    void unknownTourIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/tours/SOMETHING/seen").header("Authorization", auth(user)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAToken() throws Exception {
        mockMvc.perform(get("/api/v1/tours")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/tours/FINANCE_MEMBER/seen")).andExpect(status().isUnauthorized());
    }
}
