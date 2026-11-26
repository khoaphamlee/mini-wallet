package com.khoaphamlee.wallet.auth.api;

import static com.khoaphamlee.wallet.auth.AuthTestData.randomEmail;
import static com.khoaphamlee.wallet.auth.AuthTestData.randomPhone;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.khoaphamlee.wallet.TestcontainersConfiguration;
import com.khoaphamlee.wallet.auth.application.RefreshTokenService;
import com.khoaphamlee.wallet.auth.application.RegistrationService;
import com.khoaphamlee.wallet.auth.domain.RefreshToken;
import com.khoaphamlee.wallet.auth.domain.User;
import com.khoaphamlee.wallet.auth.infra.RefreshTokenRepository;
import com.khoaphamlee.wallet.common.web.TraceIdFilter;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** US-02 through the HTTP API, with the real security filters and a real PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LoginApiTests {

    private static final String PASSWORD = "Password1";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TraceIdFilter traceIdFilter;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private JwtDecoder jwtDecoder;

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
    void validCredentialsReturnTokenPair() throws Exception {
        User user = registrationService.register(randomEmail(), randomPhone(), "Pham Khoa", PASSWORD);

        MvcResult result = login(user.getEmail(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        // The access token is a JWT for this user with the CUSTOMER role.
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CUSTOMER");
    }

    @Test
    void onlyTheHashOfTheRefreshTokenIsStored() throws Exception {
        User user = registrationService.register(randomEmail(), randomPhone(), "Pham Khoa", PASSWORD);

        MvcResult result = login(user.getEmail(), PASSWORD).andReturn();
        String rawToken = JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");

        assertThat(refreshTokens.findByTokenHash(rawToken)).isEmpty();
        RefreshToken stored = refreshTokens
                .findByTokenHash(RefreshTokenService.hash(rawToken))
                .orElseThrow();
        assertThat(stored.getUserId()).isEqualTo(user.getId());
        assertThat(stored.getExpiresAt())
                .isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        User user = registrationService.register(randomEmail(), randomPhone(), "Pham Khoa", PASSWORD);

        login(user.getEmail().toUpperCase(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameAnswer() throws Exception {
        User user = registrationService.register(randomEmail(), randomPhone(), "Pham Khoa", PASSWORD);

        String wrongPassword = login(user.getEmail(), "WrongPassword9")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String unknownEmail = login(randomEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Same body apart from the per-request trace id: nothing tells which emails exist.
        assertThat(withoutTraceId(unknownEmail)).isEqualTo(withoutTraceId(wrongPassword));
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        User user = registrationService.register(randomEmail(), randomPhone(), "Pham Khoa", PASSWORD);
        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", user.getId());

        login(user.getEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String withoutTraceId(String json) {
        return json.replaceAll("\"traceId\":\"[^\"]*\"", "");
    }
}
