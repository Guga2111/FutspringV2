package com.futspring.backend.domain.stats.repository;

import com.futspring.backend.domain.daily.entity.Daily;
import com.futspring.backend.domain.stats.entity.DailyAward;
import com.futspring.backend.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DailyAwardRepository extends JpaRepository<DailyAward, Long> {

    Optional<DailyAward> findByDaily(Daily daily);

    // [category, count] for one user across every pelada; category is ARTILHEIRO, GARCOM, PUSKAS or BOLA_MURCHA
    @Query(nativeQuery = true, value = """
        SELECT 'ARTILHEIRO', COUNT(*) FROM daily_award_artilheiro WHERE user_id = :userId
        UNION ALL
        SELECT 'GARCOM', COUNT(*) FROM daily_award_garcom WHERE user_id = :userId
        UNION ALL
        SELECT 'PUSKAS', COUNT(*) FROM daily_award_puskas WHERE user_id = :userId
        UNION ALL
        SELECT 'BOLA_MURCHA', COUNT(*) FROM daily_award_wiltball WHERE user_id = :userId
        """)
    List<Object[]> countAwardsByUser(@Param("userId") Long userId);

    // [category, count] for one user in one pelada
    @Query(nativeQuery = true, value = """
        SELECT 'ARTILHEIRO', COUNT(*) FROM daily_award_artilheiro w
          JOIN daily_awards da ON da.id = w.daily_award_id JOIN dailies d ON d.id = da.daily_id
          WHERE w.user_id = :userId AND d.pelada_id = :peladaId
        UNION ALL
        SELECT 'GARCOM', COUNT(*) FROM daily_award_garcom w
          JOIN daily_awards da ON da.id = w.daily_award_id JOIN dailies d ON d.id = da.daily_id
          WHERE w.user_id = :userId AND d.pelada_id = :peladaId
        UNION ALL
        SELECT 'PUSKAS', COUNT(*) FROM daily_award_puskas w
          JOIN daily_awards da ON da.id = w.daily_award_id JOIN dailies d ON d.id = da.daily_id
          WHERE w.user_id = :userId AND d.pelada_id = :peladaId
        UNION ALL
        SELECT 'BOLA_MURCHA', COUNT(*) FROM daily_award_wiltball w
          JOIN daily_awards da ON da.id = w.daily_award_id JOIN dailies d ON d.id = da.daily_id
          WHERE w.user_id = :userId AND d.pelada_id = :peladaId
        """)
    List<Object[]> countAwardsByUserAndPelada(@Param("userId") Long userId, @Param("peladaId") Long peladaId);

    // [category, userId, username, image, count] for every winner of a pelada, in one query
    @Query(nativeQuery = true, value = """
        SELECT 'ARTILHEIRO', u.id, u.username, u.image, COUNT(*) FROM daily_award_artilheiro w
          JOIN daily_awards da ON da.id = w.daily_award_id JOIN dailies d ON d.id = da.daily_id
          JOIN users u ON u.id = w.user_id
          WHERE d.pelada_id = :peladaId GROUP BY u.id, u.username, u.image
        UNION ALL
        SELECT 'GARCOM', u.id, u.username, u.image, COUNT(*) FROM daily_award_garcom w
          JOIN daily_awards da ON da.id = w.daily_award_id JOIN dailies d ON d.id = da.daily_id
          JOIN users u ON u.id = w.user_id
          WHERE d.pelada_id = :peladaId GROUP BY u.id, u.username, u.image
        UNION ALL
        SELECT 'PUSKAS', u.id, u.username, u.image, COUNT(*) FROM daily_award_puskas w
          JOIN daily_awards da ON da.id = w.daily_award_id JOIN dailies d ON d.id = da.daily_id
          JOIN users u ON u.id = w.user_id
          WHERE d.pelada_id = :peladaId GROUP BY u.id, u.username, u.image
        UNION ALL
        SELECT 'BOLA_MURCHA', u.id, u.username, u.image, COUNT(*) FROM daily_award_wiltball w
          JOIN daily_awards da ON da.id = w.daily_award_id JOIN dailies d ON d.id = da.daily_id
          JOIN users u ON u.id = w.user_id
          WHERE d.pelada_id = :peladaId GROUP BY u.id, u.username, u.image
        """)
    List<Object[]> countWinnersByPelada(@Param("peladaId") Long peladaId);

    @Query("SELECT u.id, d.dailyDate FROM DailyAward da JOIN da.puskasWinners u JOIN da.daily d WHERE u IN :users")
    List<Object[]> findPuskasDatesByUsers(@Param("users") Collection<User> users);
}
