package com.futspring.backend.domain.pelada;

import com.futspring.backend.domain.finance.account.FinanceManager;
import com.futspring.backend.domain.finance.account.FinanceManagerRepository;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.support.BaseIntegrationTest;
import com.futspring.backend.support.QueryCounter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// isFinanceManager on the pelada lists: the caller's own designation, never derived from admin/creator
class PeladaFinanceManagerFlagTest extends BaseIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PeladaRepository peladaRepository;
    @Autowired private FinanceManagerRepository financeManagerRepository;
    @Autowired private PeladaService peladaService;
    @Autowired private QueryCounter queryCounter;
    @Autowired private EntityManager entityManager;

    private User creator;
    private User manager;
    private User member;

    @BeforeEach
    void setUp() {
        creator = user("fm-creator");
        manager = user("fm-manager");
        member = user("fm-member");
    }

    @Test
    void myPeladas_flagsOnlyTheResponsaveis() throws Exception {
        Pelada pelada = pelada("Com caixa");
        designate(pelada, manager);

        // the creator is an admin but not a responsável here
        expectFlag(creator, "/api/v1/peladas/my", true, false);
        expectFlag(manager, "/api/v1/peladas/my", false, true);
        expectFlag(member, "/api/v1/peladas/my", false, false);
    }

    @Test
    void sharedPeladas_flagTheCaller() throws Exception {
        Pelada pelada = pelada("Compartilhada");
        designate(pelada, manager);

        expectFlag(manager, "/api/v1/users/" + member.getId() + "/peladas", false, true);
        expectFlag(member, "/api/v1/users/" + manager.getId() + "/peladas", false, false);
    }

    @Test
    void myPeladas_runsAConstantNumberOfStatements() {
        for (int i = 0; i < 2; i++) designate(pelada("Pequena " + i), manager);
        long few = statementsOfMyPeladas();
        for (int i = 0; i < 18; i++) designate(pelada("Grande " + i), manager);
        long many = statementsOfMyPeladas();

        assertThat(many).isEqualTo(few);
    }

    private long statementsOfMyPeladas() {
        entityManager.flush();
        entityManager.clear();
        peladaService.getMyPeladas(manager.getEmail()); // warm-up
        entityManager.clear();
        return queryCounter.statements(() -> peladaService.getMyPeladas(manager.getEmail()));
    }

    private void expectFlag(User caller, String path, boolean isAdmin, boolean isFinanceManager) throws Exception {
        mockMvc.perform(get(path).header("Authorization", bearerToken(caller.getId(), caller.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isAdmin").value(isAdmin))
                .andExpect(jsonPath("$[0].isFinanceManager").value(isFinanceManager));
    }

    private User user(String name) {
        return userRepository.save(User.builder().emailVerifiedAt(Instant.EPOCH).email(name + "@test.com")
                .username(name).password("hash").build());
    }

    private Pelada pelada(String name) {
        return peladaRepository.save(Pelada.builder().name(name).dayOfWeek("FRIDAY").timeOfDay("18:00")
                .duration(2f).creator(creator).members(new HashSet<>(Set.of(creator, manager, member)))
                .admins(new HashSet<>(Set.of(creator))).numberOfTeams(2).playersPerTeam(5).build());
    }

    private void designate(Pelada pelada, User user) {
        FinanceManager row = new FinanceManager();
        row.setPeladaId(pelada.getId());
        row.setUserId(user.getId());
        financeManagerRepository.save(row);
    }
}
