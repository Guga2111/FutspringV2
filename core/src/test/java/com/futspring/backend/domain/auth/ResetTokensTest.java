package com.futspring.backend.domain.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResetTokensTest {

    @Test
    void generate_returnsUrlSafeUniqueTokens() {
        String first = ResetTokens.generate();
        String second = ResetTokens.generate();

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hash_isDeterministicHexSha256() {
        assertThat(ResetTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .isEqualTo(ResetTokens.hash("abc"));
        assertThat(ResetTokens.hash("abd")).isNotEqualTo(ResetTokens.hash("abc"));
    }
}
