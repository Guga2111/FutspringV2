package com.futspring.backend.contract;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.futspring.backend.domain.auth.dto.AuthResponseDTO;
import com.futspring.backend.domain.auth.dto.ForgotPasswordRequestDTO;
import com.futspring.backend.domain.auth.dto.LoginRequestDTO;
import com.futspring.backend.domain.auth.dto.RegisterRequestDTO;
import com.futspring.backend.domain.auth.dto.ResetPasswordRequestDTO;
import com.futspring.backend.domain.chat.dto.MessageDTO;
import com.futspring.backend.domain.chat.dto.SendMessageRequest;
import com.futspring.backend.domain.daily.dto.CreateDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.DailyDetailDTO;
import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.dto.FinalizeDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.MatchResultDTO;
import com.futspring.backend.domain.daily.dto.PopulateDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.SwapPlayersRequestDTO;
import com.futspring.backend.domain.daily.dto.UpdateDailyStatusRequestDTO;
import com.futspring.backend.domain.daily.dto.UpdateTeamColorRequestDTO;
import com.futspring.backend.domain.daily.dto.UpdateTeamNameRequestDTO;
import com.futspring.backend.domain.notification.dto.NotificationDTO;
import com.futspring.backend.domain.notification.dto.NotificationPreferenceDTO;
import com.futspring.backend.domain.notification.dto.NotificationPushDTO;
import com.futspring.backend.domain.notification.dto.UnreadCountDTO;
import com.futspring.backend.domain.notification.dto.UnsubscribeRequestDTO;
import com.futspring.backend.domain.pelada.dto.AddPlayerRequestDTO;
import com.futspring.backend.domain.pelada.dto.BulkRemovePlayersRequestDTO;
import com.futspring.backend.domain.pelada.dto.BulkSetAdminRequestDTO;
import com.futspring.backend.domain.pelada.dto.BulkSetMensalistaRequestDTO;
import com.futspring.backend.domain.pelada.dto.BulkSetStarsRequestDTO;
import com.futspring.backend.domain.pelada.dto.CreatePeladaRequestDTO;
import com.futspring.backend.domain.pelada.dto.PeladaDetailResponseDTO;
import com.futspring.backend.domain.pelada.dto.PeladaMemberDTO;
import com.futspring.backend.domain.pelada.dto.PeladaResponseDTO;
import com.futspring.backend.domain.pelada.dto.SetAdminRequestDTO;
import com.futspring.backend.domain.pelada.dto.SetMensalistaRequestDTO;
import com.futspring.backend.domain.pelada.dto.TransferCreatorRequestDTO;
import com.futspring.backend.domain.pelada.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.domain.pelada.dto.UpdatePlayerStarsRequestDTO;
import com.futspring.backend.domain.stats.dto.PeladaAttendanceDTO;
import com.futspring.backend.domain.stats.dto.PeladaAwardsDTO;
import com.futspring.backend.domain.stats.dto.PlayerPeladaHistoryDTO;
import com.futspring.backend.domain.stats.dto.PlayerPeladaStatsDTO;
import com.futspring.backend.domain.stats.dto.RankingDTO;
import com.futspring.backend.domain.stats.dto.StatsDTO;
import com.futspring.backend.domain.stats.dto.UserMatchHistoryDTO;
import com.futspring.backend.domain.stats.dto.UserStatsTimelineDTO;
import com.futspring.backend.domain.user.dto.ProfileDTO;
import com.futspring.backend.domain.user.dto.PublicUserDTO;
import com.futspring.backend.domain.user.dto.UpdateProfileRequest;
import com.futspring.backend.shared.exception.ErrorResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The JSON contract with the web app, with the application's ObjectMapper. Responses: every response DTO, with
 * every field set (ContractFixtures), must serialize to src/test/resources/contract/&lt;Dto&gt;.json. Requests: the
 * bodies the client sends (client/src/api) in contract/requests/&lt;Dto&gt;.json must deserialize and keep every value.
 * Trees are compared, so property order doesn't matter. Regenerate the response snapshots with
 * -Dcontract.update=true only for an intended contract change, reviewed against client/src/types.
 */
@JsonTest
class ContractSnapshotTest {

    private static final Path CONTRACT = Path.of("src/test/resources/contract");
    private static final boolean UPDATE = Boolean.getBoolean("contract.update");

    @Autowired
    private ObjectMapper objectMapper;

    // Top-level response bodies; nested DTOs (members, teams, refs…) are covered inside them
    static Stream<Class<?>> responses() {
        return Stream.of(
                AuthResponseDTO.class, ProfileDTO.class, PublicUserDTO.class,
                PeladaResponseDTO.class, PeladaDetailResponseDTO.class, PeladaMemberDTO.class,
                DailyListItemDTO.class, DailyDetailDTO.class, DailyDetailDTO.TeamDTO.class, DailyDetailDTO.MatchDTO.class,
                RankingDTO.class, StatsDTO.class, PeladaAwardsDTO.class, PeladaAttendanceDTO.class,
                PlayerPeladaStatsDTO.class, PlayerPeladaHistoryDTO.class, UserMatchHistoryDTO.class,
                UserStatsTimelineDTO.class,
                MessageDTO.class,
                NotificationDTO.class, NotificationPushDTO.class, NotificationPreferenceDTO.class, UnreadCountDTO.class,
                ErrorResponse.class);
    }

    // Request bodies sent by the client; MatchResultDTO and NotificationPreferenceDTO are sent as lists
    static Stream<Class<?>> requests() {
        return Stream.of(
                LoginRequestDTO.class, RegisterRequestDTO.class, ForgotPasswordRequestDTO.class,
                ResetPasswordRequestDTO.class,
                CreatePeladaRequestDTO.class, UpdatePeladaRequestDTO.class, AddPlayerRequestDTO.class,
                SetAdminRequestDTO.class, SetMensalistaRequestDTO.class, UpdatePlayerStarsRequestDTO.class,
                BulkSetAdminRequestDTO.class, BulkSetMensalistaRequestDTO.class, BulkSetStarsRequestDTO.class,
                BulkRemovePlayersRequestDTO.class, TransferCreatorRequestDTO.class,
                CreateDailyRequestDTO.class, SwapPlayersRequestDTO.class, UpdateDailyStatusRequestDTO.class,
                UpdateTeamNameRequestDTO.class, UpdateTeamColorRequestDTO.class, MatchResultDTO.class,
                FinalizeDailyRequestDTO.class, PopulateDailyRequestDTO.class,
                UpdateProfileRequest.class, SendMessageRequest.class,
                NotificationPreferenceDTO.class, UnsubscribeRequestDTO.class);
    }

    private static final Set<Class<?>> SENT_AS_LIST = Set.of(MatchResultDTO.class, NotificationPreferenceDTO.class);

    @ParameterizedTest(name = "{0}")
    @MethodSource("responses")
    void response_matchesItsSnapshot(Class<?> type) throws IOException {
        JsonNode actual = objectMapper.readTree(objectMapper.writeValueAsString(ContractFixtures.filled(type)));
        Path snapshot = CONTRACT.resolve(name(type) + ".json");

        if (UPDATE) {
            Files.createDirectories(CONTRACT);
            Files.writeString(snapshot, objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsString(actual) + "\n");
            return;
        }
        if (!Files.exists(snapshot)) {
            fail("No snapshot for " + name(type) + ": run with -Dcontract.update=true and review it");
        }
        assertThat(actual).as(name(type)).isEqualTo(objectMapper.readTree(snapshot.toFile()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("requests")
    void request_fromTheClient_isReadWithEveryField(Class<?> type) throws IOException {
        JsonNode sent = objectMapper.readTree(CONTRACT.resolve("requests/" + name(type) + ".json").toFile());
        JavaType javaType = SENT_AS_LIST.contains(type)
                ? objectMapper.getTypeFactory().constructCollectionType(List.class, type)
                : objectMapper.constructType(type);

        Object parsed = objectMapper.readValue(objectMapper.writeValueAsString(sent), javaType);

        // what the server read, written back, must still hold every value the client sent
        assertContains(objectMapper.valueToTree(parsed), sent, name(type));
    }

    private static void assertContains(JsonNode actual, JsonNode expected, String path) {
        if (expected.isObject()) {
            assertThat(actual.isObject()).as(path).isTrue();
            for (Iterator<Map.Entry<String, JsonNode>> it = expected.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> field = it.next();
                assertThat(actual.has(field.getKey())).as(path + "." + field.getKey() + " was not read").isTrue();
                assertContains(actual.get(field.getKey()), field.getValue(), path + "." + field.getKey());
            }
        } else if (expected.isArray()) {
            assertThat(actual.isArray()).as(path).isTrue();
            assertThat(actual.size()).as(path).isEqualTo(expected.size());
            for (int i = 0; i < expected.size(); i++) {
                assertContains(actual.get(i), expected.get(i), path + "[" + i + "]");
            }
        } else if (expected.isNumber()) {
            assertThat(actual.isNumber()).as(path).isTrue();
            assertThat(actual.decimalValue()).as(path).isEqualByComparingTo(expected.decimalValue());
        } else {
            assertThat(actual).as(path).isEqualTo(expected);
        }
    }

    // Nested classes get their enclosing class as a prefix: DailyDetailDTO.TeamDTO
    private static String name(Class<?> type) {
        return type.getEnclosingClass() == null ? type.getSimpleName()
                : type.getEnclosingClass().getSimpleName() + "." + type.getSimpleName();
    }
}
