package com.khoaphamlee.wallet.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.khoaphamlee.wallet.TestcontainersConfiguration;
import com.khoaphamlee.wallet.auth.infra.JwtProperties;
import com.khoaphamlee.wallet.auth.infra.JwtService;
import com.khoaphamlee.wallet.common.web.TraceIdFilter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/** Runs requests through the real Spring Security filter chain and checks who gets in. */
@SpringBootTest
@Import({TestcontainersConfiguration.class, SecurityConfigTests.TestController.class})
class SecurityConfigTests {

    private static final String PROTECTED_URL = "/api/v1/test/me";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TraceIdFilter traceIdFilter;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtProperties jwtProperties;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(traceIdFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void requestWithoutTokenIsRejectedWithProblemDetail() throws Exception {
        mockMvc.perform(get(PROTECTED_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void malformedTokenIsRejected() throws Exception {
        mockMvc.perform(get(PROTECTED_URL).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        // A token issued one hour ago with a 15 minute lifetime expired 45 minutes ago.
        Clock oneHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        String expiredToken =
                new JwtService(jwtEncoder, jwtProperties, oneHourAgo).createAccessToken(UUID.randomUUID(), "CUSTOMER");

        mockMvc.perform(get(PROTECTED_URL).header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void validTokenReachesTheControllerWithUserIdAndRole() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = jwtService.createAccessToken(userId, "CUSTOMER");

        mockMvc.perform(get(PROTECTED_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.authorities").value(hasItem("ROLE_CUSTOMER")));
    }

    @Test
    void customerCannotCallAdminEndpoints() throws Exception {
        String token = jwtService.createAccessToken(UUID.randomUUID(), "CUSTOMER");

        mockMvc.perform(get("/api/v1/admin/wallets/" + UUID.randomUUID()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void publicEndpointsDoNotNeedToken() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

        // There is no login controller yet (issue #5), but the request must get past security.
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(
                        result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    void accessTokenHoldsUserIdRoleAndFifteenMinuteLifetime() {
        UUID userId = UUID.randomUUID();

        Jwt jwt = jwtDecoder.decode(jwtService.createAccessToken(userId, "ADMIN"));

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    /** A protected endpoint that only exists in this test and echoes the current user. */
    @RestController
    static class TestController {

        @GetMapping(PROTECTED_URL)
        Map<String, Object> me(Authentication authentication) {
            return Map.of(
                    "userId", authentication.getName(),
                    "authorities",
                            authentication.getAuthorities().stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .toList());
        }
    }
}
