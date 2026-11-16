package com.khoaphamlee.wallet.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.khoaphamlee.wallet.TestcontainersConfiguration;
import com.khoaphamlee.wallet.common.web.TraceIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * Calls a test-only controller that throws each kind of error and checks the JSON the client gets.
 *
 * <p>Spring Security is not applied here on purpose: these tests are about the error format only.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, GlobalExceptionHandlerTests.TestController.class})
class GlobalExceptionHandlerTests {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TraceIdFilter traceIdFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(traceIdFilter)
                .build();
    }

    @Test
    void businessExceptionReturnsProblemDetailWithCodeAndTraceId() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"))
                .andExpect(jsonPath("$.title").value("Insufficient balance"))
                .andExpect(result -> {
                    // The trace id in the body must be the same one sent in the response header.
                    String traceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
                    assertThat(traceId).hasSize(16);
                    jsonPath("$.traceId").value(traceId).match(result);
                });
    }

    @Test
    void invalidBodyReturnsValidationErrorListingEachField() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "email")));
    }

    @Test
    void unexpectedExceptionReturns500WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Unexpected error"))
                .andExpect(content().string(not(containsString("secret-internal-detail"))));
    }

    @Test
    void springHttpErrorKeepsItsStatusAndGetsCodeAndTraceId() throws Exception {
        // /test/business only accepts GET, so POST makes Spring raise its own 405 error.
        mockMvc.perform(post("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    /** Endpoints that exist only in this test, each one failing in a different way. */
    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret-internal-detail");
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody TestRequest request) {}
    }

    record TestRequest(@NotBlank String name, @Email String email) {}
}
