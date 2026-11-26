package com.khoaphamlee.wallet.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.khoaphamlee.wallet.TestcontainersConfiguration;
import com.khoaphamlee.wallet.auth.domain.User;
import com.khoaphamlee.wallet.auth.infra.UserRepository;
import com.khoaphamlee.wallet.common.web.TraceIdFilter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** US-01 through the HTTP API, with the real security filters and a real PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegistrationApiTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TraceIdFilter traceIdFilter;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(traceIdFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void registersCustomerWithNormalizedEmailAndPhone() throws Exception {
        String email = randomEmail();
        String phone = randomPhone();

        register(email.toUpperCase(), phone, "Pham Khoa", "Password1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.phone").value("+84" + phone.substring(1)))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.pinSet").value(false))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = users.findByEmail(email).orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("Password1");
        assertThat(passwordEncoder.matches("Password1", saved.getPasswordHash()))
                .isTrue();
    }

    @Test
    void sameEmailInDifferentCaseIsRejected() throws Exception {
        String email = randomEmail();
        register(email, randomPhone(), "First", "Password1").andExpect(status().isCreated());

        register(email.toUpperCase(), randomPhone(), "Second", "Password1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void samePhoneInAnotherFormatIsRejected() throws Exception {
        String phone = randomPhone();
        register(randomEmail(), phone, "First", "Password1").andExpect(status().isCreated());

        register(randomEmail(), "+84" + phone.substring(1), "Second", "Password1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_EXISTS"));
    }

    @Test
    void weakPasswordAndInvalidPhoneAreReportedPerField() throws Exception {
        register(randomEmail(), "12345", "Pham Khoa", "12345678")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("phone")));
    }

    private ResultActions register(String email, String phone, String fullName, String password) throws Exception {
        String body = """
                {"email": "%s", "phone": "%s", "fullName": "%s", "password": "%s"}
                """.formatted(email, phone, fullName, password);
        return mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    static String randomEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /** A random valid number like 0912345678, so tests never collide with each other. */
    static String randomPhone() {
        return "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }
}
