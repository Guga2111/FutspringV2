package com.futspring.backend.contract;

import com.futspring.backend.domain.daily.dto.DailyDetailDTO;
import com.futspring.backend.domain.daily.dto.DailyListItemDTO;
import com.futspring.backend.domain.daily.dto.FinalizeDailyRequestDTO;
import com.futspring.backend.domain.daily.dto.MatchResultDTO;
import com.futspring.backend.domain.daily.dto.PopulateDailyRequestDTO;
import com.futspring.backend.domain.notification.dto.NotificationDTO;
import com.futspring.backend.domain.pelada.dto.CreatePeladaRequestDTO;
import com.futspring.backend.domain.pelada.dto.UpdatePeladaRequestDTO;
import com.futspring.backend.domain.user.User;
import com.futspring.backend.domain.user.dto.ProfileDTO;
import com.futspring.backend.domain.user.dto.UpdateProfileRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JsonTest
class RecordContractTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @Autowired
    private JsonMapper mapper;

    @BeforeAll
    static void openValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void omittedCreationSettings_keepDefaults() {
        CreatePeladaRequestDTO request = mapper.readValue("""
                {"name":"Pelada", "dayOfWeek":"FRIDAY", "timeOfDay":"20:00", "duration":1.5}
                """, CreatePeladaRequestDTO.class);

        assertThat(request.numberOfTeams()).isEqualTo(2);
        assertThat(request.playersPerTeam()).isEqualTo(5);
        assertThat(request.autoCreateDailyEnabled()).isFalse();
        assertThat(request.maxMensalistas()).isZero();
        assertThat(request.timeZone()).isNull();
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "numberOfTeams, 0, Mínimo de 2 times",
            "numberOfTeams, 1, Mínimo de 2 times",
            "numberOfTeams, 11, Máximo de 10 times",
            "playersPerTeam, 0, Mínimo de 2 jogadores por time",
            "playersPerTeam, 1, Mínimo de 2 jogadores por time",
            "playersPerTeam, 21, Máximo de 20 jogadores por time",
            "maxMensalistas, -1, O limite de mensalistas não pode ser negativo"
    })
    void explicitInvalidCreationSettings_areNotDefaulted(String field, int value, String message) {
        CreatePeladaRequestDTO request = mapper.readValue("""
                {"name":"Pelada", "dayOfWeek":"FRIDAY", "timeOfDay":"20:00", "duration":1.5, "%s":%d}
                """.formatted(field, value), CreatePeladaRequestDTO.class);

        assertThat(mapper.valueToTree(request).get(field).intValue()).isEqualTo(value);
        assertThat(validator.validate(request))
                .anySatisfy(v -> {
                    assertThat(v.getPropertyPath().toString()).isEqualTo(field);
                    assertThat(v.getMessage()).isEqualTo(message);
                });
    }

    @Test
    void partialUpdates_keepAbsentAndNullFieldsUnset_andPreserveFalseZeroAndEmptyStrings() {
        UpdatePeladaRequestDTO absent = mapper.readValue("{}", UpdatePeladaRequestDTO.class);
        UpdatePeladaRequestDTO nulls = mapper.readValue("""
                {"name":null, "numberOfTeams":null, "playersPerTeam":null,
                 "autoCreateDailyEnabled":null, "maxMensalistas":null}
                """, UpdatePeladaRequestDTO.class);
        assertThat(nulls).isEqualTo(absent);
        assertThat(absent.numberOfTeams()).isNull();
        assertThat(absent.playersPerTeam()).isNull();
        assertThat(absent.autoCreateDailyEnabled()).isNull();
        assertThat(absent.maxMensalistas()).isNull();
        assertThat(validator.validate(absent)).isEmpty();

        UpdatePeladaRequestDTO update = mapper.readValue("""
                {"autoCreateDailyEnabled":false, "maxMensalistas":0, "address":"", "reference":""}
                """, UpdatePeladaRequestDTO.class);
        assertThat(update.autoCreateDailyEnabled()).isFalse();
        assertThat(update.maxMensalistas()).isZero();
        assertThat(update.address()).isEmpty();
        assertThat(update.reference()).isEmpty();
        assertThat(update.name()).isNull();
        assertThat(validator.validate(update)).isEmpty();

        UpdateProfileRequest profile = mapper.readValue("{}", UpdateProfileRequest.class);
        assertThat(profile.username()).isNull();
        assertThat(profile.position()).isNull();
        assertThat(validator.validate(profile)).isEmpty();
        assertThat(mapper.readValue("{\"position\":\"\"}", UpdateProfileRequest.class).position()).isEmpty();
    }

    @Test
    void omittedMatchInputs_keepZeroScoresAndOptionalFields() {
        MatchResultDTO request = mapper.readValue("{\"team1Id\":1,\"team2Id\":2}", MatchResultDTO.class);
        assertThat(request.matchId()).isNull();
        assertThat(request.playerStats()).isNull();
        assertThat(request.team1Score()).isZero();
        assertThat(request.team2Score()).isZero();
        assertThat(validator.validate(request)).isEmpty();
    }

    static Stream<Arguments> primitiveInputs() {
        return Stream.of(
                Arguments.of(CreatePeladaRequestDTO.class, "numberOfTeams", "2"),
                Arguments.of(CreatePeladaRequestDTO.class, "playersPerTeam", "5"),
                Arguments.of(CreatePeladaRequestDTO.class, "maxMensalistas", "0"),
                Arguments.of(CreatePeladaRequestDTO.class, "autoCreateDailyEnabled", "false"),
                Arguments.of(MatchResultDTO.class, "team1Score", "0"),
                Arguments.of(MatchResultDTO.class, "team2Score", "0"),
                Arguments.of(MatchResultDTO.PlayerStatInputDTO.class, "goals", "0"),
                Arguments.of(MatchResultDTO.PlayerStatInputDTO.class, "assists", "0"),
                Arguments.of(PopulateDailyRequestDTO.ParsedPlayerDTO.class, "totalGoals", "0"),
                Arguments.of(PopulateDailyRequestDTO.ParsedPlayerDTO.class, "totalAssists", "0"),
                Arguments.of(PopulateDailyRequestDTO.ParsedMatchDTO.class, "team1Score", "0"),
                Arguments.of(PopulateDailyRequestDTO.ParsedMatchDTO.class, "team2Score", "0"));
    }

    @ParameterizedTest(name = "{0}.{1}: omitted defaults, explicit null fails")
    @MethodSource("primitiveInputs")
    void primitiveInputs_distinguishOmissionFromExplicitNull(Class<?> type, String field, String defaultValue) {
        JsonNode omitted = mapper.valueToTree(mapper.readValue("{}", type));
        assertThat(omitted.get(field).toString()).isEqualTo(defaultValue);
        assertThatThrownBy(() -> mapper.readValue("{\"%s\":null}".formatted(field), type))
                .isInstanceOf(MismatchedInputException.class);
    }

    @Test
    void nestedMatchStats_keepValidationPathsAndMessages() {
        MatchResultDTO request = mapper.readValue("""
                {"team1Id":1,"team2Id":2,"playerStats":[{"userId":null,"goals":-1,"assists":-2}]}
                """, MatchResultDTO.class);
        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath() + ": " + v.getMessage())
                .containsExactlyInAnyOrder(
                        "playerStats[0].userId: Informe o jogador",
                        "playerStats[0].goals: Gols não podem ser negativos",
                        "playerStats[0].assists: Assistências não podem ser negativas");
    }

    @Test
    void nestedImportedTeamsAndMatches_keepValidationAtEveryLevel() {
        PopulateDailyRequestDTO request = mapper.readValue("""
                {"teams":[{"colorName":"Azul","colorHex":"bad","players":[{"totalGoals":-1}]}],
                 "matches":[{"team1ColorName":"Azul","team2ColorName":"","team1Score":-1}]}
                """, PopulateDailyRequestDTO.class);
        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("teams[0].colorHex", "teams[0].players[0].userId",
                        "teams[0].players[0].totalGoals", "matches[0].team2ColorName", "matches[0].team1Score");

        FinalizeDailyRequestDTO awards = mapper.readValue("{\"puskasWinnerIds\":[null]}", FinalizeDailyRequestDTO.class);
        assertThat(validator.validate(awards)).hasSize(1);
        assertThat(mapper.readValue("{}", FinalizeDailyRequestDTO.class).wiltballWinnerIds()).isNull();
    }

    @Test
    void nullableResponses_preserveOmissionAndExplicitNulls() {
        User user = User.builder().id(1L).username("Jogador").email("owner@example.com").build();
        JsonNode publicProfile = mapper.valueToTree(ProfileDTO.from(user, false, null));
        assertThat(publicProfile.has("email")).isFalse();
        assertThat(publicProfile.has("stars")).isFalse();
        assertThat(mapper.valueToTree(ProfileDTO.from(user, true, 3)).get("email").asString())
                .isEqualTo("owner@example.com");

        JsonNode player = mapper.valueToTree(DailyDetailDTO.PlayerDTO.builder().id(1L).build());
        assertThat(player.has("isMensalista")).isFalse();
        assertThat(player.has("confirmedAt")).isFalse();
        assertThat(player.get("image").isNull()).isTrue();
        assertThat(mapper.valueToTree(DailyDetailDTO.PlayerDTO.builder().isMensalista(false).build())
                .get("isMensalista").booleanValue()).isFalse();

        JsonNode daily = mapper.valueToTree(DailyListItemDTO.builder().build());
        assertThat(daily.get("championImage").isNull()).isTrue();
        assertThat(daily.get("confirmOpensAt").isNull()).isTrue();
        JsonNode detail = mapper.valueToTree(DailyDetailDTO.builder().build());
        assertThat(detail.get("award").isNull()).isTrue();
        assertThat(detail.get("myBump").isNull()).isTrue();
        JsonNode match = mapper.valueToTree(DailyDetailDTO.MatchDTO.builder().build());
        assertThat(match.get("team1Score").isNull()).isTrue();
        assertThat(match.get("winnerId").isNull()).isTrue();
        JsonNode notification = mapper.valueToTree(NotificationDTO.builder().build());
        for (String field : new String[]{"award", "readAt", "pelada", "daily", "actor", "team"}) {
            assertThat(notification.has(field)).as(field).isTrue();
            assertThat(notification.get(field).isNull()).as(field).isTrue();
        }
    }
}
