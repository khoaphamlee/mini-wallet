package com.khoaphamlee.wallet.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenServiceTests {

    @Test
    void hashIsSha256AsLowercaseHex() {
        // Known SHA-256 test vector for "abc".
        assertThat(RefreshTokenService.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
