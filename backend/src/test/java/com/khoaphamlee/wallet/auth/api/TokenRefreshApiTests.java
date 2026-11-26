package com.khoaphamlee.wallet.auth.api;

import static com.khoaphamlee.wallet.auth.AuthTestData.randomEmail;
import static com.khoaphamlee.wallet.auth.AuthTestData.randomPhone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.khoaphamlee.wallet.TestcontainersConfiguration;
import com.khoaphamlee.wallet.auth.application.LoginResult;
import com.khoaphamlee.wallet.auth.application.LoginService;
import com.khoaphamlee.wallet.auth.application.RefreshTokenService;
import com.khoaphamlee.wallet.auth.application.RegistrationService;
import com.khoaphamlee.wallet.auth.domain.RefreshToken;
import com.khoaphamlee.wallet.auth.domain.User;
import com.khoaphamlee.wallet.auth.infra.RefreshTokenRepository;
import com.khoaphamlee.wallet.common.web.TraceIdFilter;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** US-03 through the HTTP API, with the real security filters and a real PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TokenRefreshApiTests {

    private static final String PASSWORD = "Password1";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TraceIdFilter traceIdFilter;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(traceIdFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void refreshReturnsNewPairAndTheOldTokenStopsWorking() throws Exception {
        LoginResult login = registerAndLogin();

        String body = refresh(login.refreshToken())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String newRefreshToken = JsonPath.read(body, "$.refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(login.refreshToken());

        refresh(login.refreshToken())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void reusingARevokedTokenEndsEverySessionOfTheUser() throws Exception {
        LoginResult login = registerAndLogin();
        String body = refresh(login.refreshToken()).andReturn().getResponse().getContentAsString();
        String newRefreshToken = JsonPath.read(body, "$.refreshToken");

        // The old token shows up again, as if an attacker had copied it.
        refresh(login.refreshToken()).andExpect(status().isUnauthorized());

        // The legitimate new token has been revoked too: everyone must log in again.
        refresh(newRefreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        User user = registrationService.register(randomEmail(), randomPhone(), "Pham Khoa", PASSWORD);
        String rawToken = "expired-token-" + user.getId();
        refreshTokens.save(RefreshToken.issue(
                user.getId(), RefreshTokenService.hash(rawToken), Instant.now().minusSeconds(60)));

        refresh(rawToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void unknownTokenIsRejected() throws Exception {
        refresh("this-token-was-never-issued")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void disabledUserCannotRefresh() throws Exception {
        User user = registrationService.register(randomEmail(), randomPhone(), "Pham Khoa", PASSWORD);
        LoginResult login = loginService.login(user.getEmail(), PASSWORD);
        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", user.getId());

        refresh(login.refreshToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutNeedsAccessTokenAndRevokesTheRefreshToken() throws Exception {
        LoginResult login = registerAndLogin();
        String body = """
                {"refreshToken": "%s"}
                """.formatted(login.refreshToken());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        refresh(login.refreshToken()).andExpect(status().isUnauthorized());
    }

    private LoginResult registerAndLogin() {
        User user = registrationService.register(randomEmail(), randomPhone(), "Pham Khoa", PASSWORD);
        return loginService.login(user.getEmail(), PASSWORD);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        String body = """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
