package com.futspring.backend.support;

import com.futspring.backend.domain.pelada.Pelada;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.shared.helper.PeladaAccessHelper;
import com.futspring.backend.domain.user.User;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Unit tests build peladas with in-memory members/admins sets. These stubs answer the repository's
 * membership exists queries from those sets (read live, so a test can still change them), so services
 * that use PeladaAccessHelper behave as with a real database.
 */
public final class MembershipStubs {

    private MembershipStubs() {
    }

    /** A real PeladaAccessHelper over a mock repository that knows the given peladas. */
    public static PeladaAccessHelper accessHelperFor(Pelada... peladas) {
        PeladaRepository repository = mock(PeladaRepository.class);
        stubMembership(repository, peladas);
        return new PeladaAccessHelper(repository);
    }

    /** Lenient stubs of the membership exists queries on an existing PeladaRepository mock. */
    public static void stubMembership(PeladaRepository repository, Pelada... peladas) {
        List<Pelada> known = Arrays.asList(peladas);
        lenient().when(repository.existsByIdAndMembers_Id(anyLong(), anyLong())).thenAnswer(inv ->
                contains(known, inv.getArgument(0), inv.getArgument(1), false));
        lenient().when(repository.existsByIdAndAdmins_Id(anyLong(), anyLong())).thenAnswer(inv ->
                contains(known, inv.getArgument(0), inv.getArgument(1), true));
        lenient().when(repository.existsByIdAndMembers_Email(anyLong(), any())).thenAnswer(inv ->
                known.stream()
                        .filter(p -> p.getId().equals(inv.getArgument(0)))
                        .anyMatch(p -> p.getMembers().stream().anyMatch(u -> u.getEmail().equals(inv.getArgument(1)))));
        lenient().when(repository.existsSharedPelada(anyLong(), anyLong())).thenAnswer(inv ->
                known.stream().anyMatch(p -> hasId(p.getMembers(), inv.getArgument(0)) && hasId(p.getMembers(), inv.getArgument(1))));
    }

    private static boolean contains(List<Pelada> known, Long peladaId, Long userId, boolean admins) {
        return known.stream()
                .filter(p -> p.getId().equals(peladaId))
                .anyMatch(p -> hasId(admins ? p.getAdmins() : p.getMembers(), userId));
    }

    private static boolean hasId(Set<User> users, Long userId) {
        return users.stream().anyMatch(u -> u.getId().equals(userId));
    }
}
