package com.agentic.sdlc.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** T009: every error is an RFC 9457 problem with a stable code and correlation id, never internal details. */
@Tag("FR-OPS-03")
class ProblemDetailsHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new ProblemDetailsHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void apiExceptionBecomesProblemWithCodeAndCorrelationId() throws Exception {
        mvc.perform(get("/fail/api").header(CorrelationIdFilter.HEADER, "corr-12345678"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("URL_SCHEME_NOT_ALLOWED"))
                .andExpect(jsonPath("$.title").value(ErrorCode.URL_SCHEME_NOT_ALLOWED.title()))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Only http and https URLs can be shortened."))
                .andExpect(jsonPath("$.correlationId").value("corr-12345678"))
                .andExpect(jsonPath("$.type").value("urn:problem-type:url-scheme-not-allowed"));
    }

    @Test
    void unexpectedExceptionIsGenericAndHidesInternals() throws Exception {
        MvcResult result = mvc.perform(get("/fail/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("SELECT").doesNotContain("secret-internal-detail").doesNotContain("at com.");
    }

    @Test
    void beanValidationFailureListsFieldErrors() throws Exception {
        mvc.perform(post("/fail/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void unreadableBodyIsAValidationFailure() throws Exception {
        mvc.perform(post("/fail/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void storeFailureIsRetryableServiceUnavailable() throws Exception {
        mvc.perform(get("/fail/store"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("STORE_UNAVAILABLE"));
    }

    @Test
    void rateLimitCarriesRetryAfter() throws Exception {
        mvc.perform(get("/fail/rate"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(7));
    }

    @RestController
    static class FailingController {

        @GetMapping("/fail/api")
        String api() {
            throw new ApiException(ErrorCode.URL_SCHEME_NOT_ALLOWED, "Only http and https URLs can be shortened.");
        }

        @GetMapping("/fail/unexpected")
        String unexpected() {
            throw new IllegalStateException("SELECT * FROM secret-internal-detail");
        }

        @PostMapping("/fail/validate")
        String validate(@Valid @RequestBody Named body) {
            return body.name();
        }

        @GetMapping("/fail/store")
        String store() {
            throw new DataAccessResourceFailureException("connection refused");
        }

        @GetMapping("/fail/rate")
        String rate() {
            throw ApiException.rateLimited("Too many requests.", 7);
        }
    }

    record Named(@NotBlank String name) {
    }
}
