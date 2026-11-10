package com.futspring.backend.domain.daily.team;

import com.futspring.backend.domain.daily.team.Team;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.shared.exception.AppException;
import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.util.Set;

/**
 * When a session's teams are good enough to start it: at least two teams, none empty, every player still
 * confirmed. Short teams and confirmed players left out never block. Shared by the start transition and the
 * manual save (which reuses the empty-team message).
 */
public final class TeamsReadiness {

    private TeamsReadiness() {
    }

    public static void requireStartable(Collection<Team> teams, Set<Long> confirmedIds) {
        if (teams.size() < 2) {
            throw new AppException(HttpStatus.CONFLICT, "Monte os times antes de iniciar a sessão");
        }
        for (Team team : teams) {
            if (team.getPlayers().isEmpty()) {
                throw new AppException(HttpStatus.CONFLICT, emptyTeam(team.getName()));
            }
        }
        for (Team team : teams) {
            for (User player : team.getPlayers()) {
                if (!confirmedIds.contains(player.getId())) {
                    throw new AppException(HttpStatus.CONFLICT,
                            player.getUsername() + " não está mais confirmada e está no " + team.getName());
                }
            }
        }
    }

    public static String emptyTeam(String teamName) {
        return "O " + teamName + " está vazio";
    }
}
