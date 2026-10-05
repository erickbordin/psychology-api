package com.psychologyapi.psychologyapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.psychologyapi.psychologyapi.support.HttpTest;

@HttpTest
class SaudeHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("sem arquivo de estado configurado o backup aparece desligado e responde 200")
    void backupDesligado() throws Exception {
        mockMvc.perform(get("/saude"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.backup").value("desligado"));
    }
}
