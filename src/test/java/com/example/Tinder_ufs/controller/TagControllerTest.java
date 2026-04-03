package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
// @Import(TestSecurityConfig.class)  ← REMOVER ESTA LINHA
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Tag novaTag(String nome, String categoria, boolean ativa) {
        return new Tag(nome + "_" + UUID.randomUUID().toString().substring(0, 6),
                "Descrição de " + nome, categoria, ativa);
    }

    @Test
    void testCriarEListarTags() throws Exception {
        mockMvc.perform(post("/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaTag("Esporte", "Lazer", true))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaTag("Música", "Cultura", true))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaTag("Viagem", "Lazer", false))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tags"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tags/ativas"))
                .andExpect(status().isOk());
    }

    @Test
    void testDeleteTagDeveSerBloqueado() throws Exception {
        MvcResult result = mockMvc.perform(post("/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaTag("Esporte", "Lazer", true))))
                .andExpect(status().isOk())
                .andReturn();

        Tag tagCriada = objectMapper.readValue(result.getResponse().getContentAsString(), Tag.class);

        mockMvc.perform(delete("/tags/" + tagCriada.getId()))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void testCriarTagComDadosInvalidos() throws Exception {
        mockMvc.perform(post("/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}