package com.rzodeczko.paymentservice.presentation.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = HealthCheckController.class)
@ActiveProfiles("test")
public class HealthCheckSliceIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthCheck_shouldReturnHttp200AndExpectedMessage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("PAYMENT SERVICE OK"));
    }

    @Test
    void healthCheck_shouldReturnJsonContentType() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void healthCheck_shouldReturn404ForUnknownPath() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isNotFound());
    }
}
