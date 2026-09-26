package com.agentic.sdlc.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** T010: a correlation id is accepted if well-formed, generated otherwise, echoed, and present in the MDC. */
@Tag("FR-OPS-04")
@Tag("FR-AUD-05")
class CorrelationIdFilterTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new EchoController()).addFilters(new CorrelationIdFilter()).build();
    }

    @Test
    void acceptsAWellFormedClientId() throws Exception {
        mvc.perform(get("/echo").header(CorrelationIdFilter.HEADER, "client-abc-12345"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, "client-abc-12345"))
                .andExpect(content().string("client-abc-12345"));
    }

    @Test
    void replacesAMalformedClientId() throws Exception {
        MvcResult result = mvc.perform(get("/echo").header(CorrelationIdFilter.HEADER, "bad id\r\ninjected"))
                .andExpect(status().isOk())
                .andReturn();
        String id = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(id).matches("[0-9a-f-]{36}");
        assertThat(result.getResponse().getContentAsString()).isEqualTo(id);
    }

    @Test
    void generatesAnIdWhenAbsent() throws Exception {
        MvcResult result = mvc.perform(get("/echo")).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void clearsTheMdcAfterTheRequest() throws Exception {
        mvc.perform(get("/echo").header(CorrelationIdFilter.HEADER, "client-abc-12345")).andExpect(status().isOk());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @RestController
    static class EchoController {
        @GetMapping("/echo")
        String echo() {
            return MDC.get(CorrelationIdFilter.MDC_KEY);
        }
    }
}
