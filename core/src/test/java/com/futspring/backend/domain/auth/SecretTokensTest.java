package com.futspring.backend.domain.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretTokensTest {

    @Test
    void generate_returnsUrlSafeUniqueTokens() {
        String first = SecretTokens.generate();
        String second = SecretTokens.generate();

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hash_isDeterministicHexSha256() {
        assertThat(SecretTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .isEqualTo(SecretTokens.hash("abc"));
        assertThat(SecretTokens.hash("abd")).isNotEqualTo(SecretTokens.hash("abc"));
    }
}
