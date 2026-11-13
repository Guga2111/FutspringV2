package com.futspring.backend.domain.file;

import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.domain.user.*;
import com.futspring.backend.domain.pelada.*;
import com.futspring.backend.domain.daily.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

class FileControllerTest extends BaseIntegrationTest {
    static final Path DIRECTORY;
    static { try { DIRECTORY = Files.createTempDirectory("private-photos"); } catch (Exception e) { throw new ExceptionInInitializerError(e); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("app.uploads.dir", DIRECTORY::toString); }
    @Autowired UserRepository users;
    @Autowired PeladaRepository peladas;
    @Autowired DailyRepository dailies;
    @Autowired FileReadService service;
    User owner, member, outsider;
    Pelada pelada;
    @BeforeEach void setUp() throws Exception {
        owner = save("owner", "avatar.jpg"); member = save("member", null); outsider = save("outsider", null);
        pelada = peladas.save(Pelada.builder().name("Private group").creator(owner).dayOfWeek("FRIDAY").timeOfDay("18:00").duration(2f)
                .image("group.png").members(new HashSet<>(List.of(owner, member))).build());
        dailies.save(Daily.builder().pelada(pelada).dailyDate(java.time.LocalDate.now()).dailyTime("18:00").championImage("champion.jpg").build());
        for (String file : List.of("avatar.jpg", "group.png", "champion.jpg", "orphan.jpg")) Files.write(DIRECTORY.resolve(file), new byte[]{1,2,3});
    }
    private User save(String name, String image) {
        return users.save(User.builder().emailVerifiedAt(Instant.EPOCH).username(name).email(name+"@test.com").password("hash").image(image).build());
    }
    @Test void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/files/avatar.jpg")).andExpect(status().isUnauthorized());
    }
    @Test void ownerAndSharedMemberCanReadButUnrelatedAccountCannot() throws Exception {
        for (String file : List.of("avatar.jpg", "group.png", "champion.jpg")) {
            for (User user : List.of(owner, member)) mockMvc.perform(get("/api/v1/files/"+file).header("Authorization",bearerToken(user.getId(),user.getEmail())))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
            mockMvc.perform(get("/api/v1/files/"+file).header("Authorization",bearerToken(outsider.getId(),outsider.getEmail()))).andExpect(status().isNotFound());
        }
    }
    @Test void removalRevokesEveryGroupPhotoAndOtherPlayersPhotos() throws Exception {
        pelada.getMembers().remove(member); peladas.saveAndFlush(pelada);
        for (String file : List.of("avatar.jpg", "group.png", "champion.jpg", "orphan.jpg"))
            mockMvc.perform(get("/api/v1/files/"+file).header("Authorization",bearerToken(member.getId(),member.getEmail()))).andExpect(status().isNotFound());
    }
    @Test void traversalIsRejectedBeforeStorageAccess() {
        assertThatThrownBy(() -> service.read("../../etc/passwd",owner.getEmail())).isInstanceOf(com.futspring.backend.shared.exception.AppException.class);
    }
}
