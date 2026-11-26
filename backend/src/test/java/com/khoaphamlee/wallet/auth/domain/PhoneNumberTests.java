package com.khoaphamlee.wallet.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Plain unit test: no Spring, no database, runs in milliseconds. */
class PhoneNumberTests {

    @ParameterizedTest
    @ValueSource(strings = {"0901234567", "84901234567", "+84901234567", " 0901234567 "})
    void normalizesEveryAcceptedFormatToPlus84(String raw) {
        assertThat(PhoneNumber.normalize(raw)).isEqualTo("+84901234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"090123456", "09012345678", "0101234567", "+1901234567", "09012abc67", ""})
    void rejectsNumbersThatAreNotVietnameseMobiles(String raw) {
        assertThatThrownBy(() -> PhoneNumber.normalize(raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
