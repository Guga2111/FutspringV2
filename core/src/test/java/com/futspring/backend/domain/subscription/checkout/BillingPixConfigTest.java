package com.futspring.backend.domain.subscription.checkout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingPixConfigTest {

    @Test
    void withoutKey_thereIsNoPix() {
        BillingPixConfig config = new BillingPixConfig("", "", "", "");

        assertThat(config.configured()).isFalse();
        assertThat(config.recipient()).isEmpty();
        assertThat(config.code(new BigDecimal("19.90"), 1L)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "11999990000, +5511999990000",
            "'(11) 99999-0000', +5511999990000",
            "1133330000, +551133330000",
            "+55 11 99999-0000, +5511999990000",
            "5511999990000, +5511999990000",
            "Contato@Futspring.com, contato@futspring.com",
            "123E4567-E12B-12D1-A456-426655440000, 123e4567-e12b-12d1-a456-426655440000"
    })
    void normalizesPhoneEmailAndRandomKeys(String key, String expected) {
        assertThat(BillingPixConfig.normalizeKey(key)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "+1 555 0100", "not a key", "119999900001234"})
    void anInvalidKey_failsTheStartup(String key) {
        assertThatThrownBy(() -> new BillingPixConfig(key, "Nome", "", "Cidade"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theCodeCarriesTheShortNameAndCityWithoutAccents_theScreenTheFullName() {
        BillingPixConfig config = new BillingPixConfig("11999990000", "João Ninguém da Silva Santos Júnior",
                "João Ninguém", "São Paulo");

        assertThat(config.recipient()).contains("João Ninguém da Silva Santos Júnior");
        assertThat(config.code(new BigDecimal("199.00"), 42L)).hasValueSatisfying(code -> assertThat(code)
                .contains("5912JOAO NINGUEM").contains("6009SAO PAULO").contains("5406199.00")
                .contains("0505FUT42"));
    }

    @Test
    void withoutMerchantName_theCodeUsesTheName() {
        BillingPixConfig config = new BillingPixConfig("11999990000", "Futspring", " ", "Recife");

        assertThat(config.code(new BigDecimal("19.90"), 1L)).hasValueSatisfying(code ->
                assertThat(code).contains("5909FUTSPRING"));
    }

    @Test
    void aNameOrCityOverTheLimit_orMissing_failsTheStartup() {
        assertThatThrownBy(() -> new BillingPixConfig("11999990000", "A".repeat(26), "", "Recife"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("25");
        assertThatThrownBy(() -> new BillingPixConfig("11999990000", "Nome", "", "Sao Jose dos Campos"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("15");
        assertThatThrownBy(() -> new BillingPixConfig("11999990000", "", "", "Recife"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new BillingPixConfig("11999990000", "Nome", "", ""))
                .isInstanceOf(IllegalStateException.class);
    }
}
