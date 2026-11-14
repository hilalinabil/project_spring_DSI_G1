package com.ticketflow.customerservice.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigDemoControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void returnsMessageFromConfiguration() throws Exception {
        mockMvc.perform(get("/api/config/message"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Message from test properties"));
    }
}
