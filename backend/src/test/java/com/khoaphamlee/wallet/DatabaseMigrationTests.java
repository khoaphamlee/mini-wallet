package com.khoaphamlee.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Checks that Flyway migrations run and that the key database constraints hold. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DatabaseMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsSystemTopupWallet() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM wallets WHERE type = 'SYSTEM' AND code = 'SYSTEM_TOPUP'", Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    void rejectsNegativeBalanceOnCustomerWallet() {
        String userId = jdbcTemplate.queryForObject("""
                INSERT INTO users (email, phone, full_name, password_hash, role, status)
                VALUES ('negative@example.com', '+84900000001', 'Test User', 'hash', 'CUSTOMER', 'ACTIVE')
                RETURNING id::text
                """, String.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                        "INSERT INTO wallets (user_id, type, balance) VALUES (?::uuid, 'USER', -1)", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
