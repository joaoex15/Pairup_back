package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
// @Import(TestSecurityConfig.class)  ← REMOVER ESTA LINHA
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private RequestPostProcessor comUsuario(String userId) {
        return (MockHttpServletRequest request) -> {
            request.setAttribute("userId", userId);
            return request;
        };
    }

    private String criarUser(String nome) throws Exception {
        String email = nome.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        User user = new User(nome, email, "teste123");
        MvcResult r = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(r.getResponse().getContentAsString(), User.class).getId();
    }

    @Test
    void darLike() throws Exception {
        String user1Id = criarUser("João");
        String user2Id = criarUser("Joana");

        mockMvc.perform(post("/likes")
                        .with(comUsuario(user2Id))
                        .param("destinoId", user1Id))
                .andExpect(status().isOk());

        mockMvc.perform(post("/likes")
                        .with(comUsuario(user1Id))
                        .param("destinoId", user2Id))
                .andExpect(status().isOk());
    }

    @Test
    void darLikeEmSimMesmoDeveRetornar400() throws Exception {
        String userId = criarUser("João");

        mockMvc.perform(post("/likes")
                        .with(comUsuario(userId))
                        .param("destinoId", userId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void darLikeSemUserIdDeveRetornar401() throws Exception {
        mockMvc.perform(post("/likes")
                        .param("destinoId", "qualquer-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listarLikesDados() throws Exception {
        String userId = criarUser("João");

        mockMvc.perform(get("/likes/dados")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void listarLikesRecebidos() throws Exception {
        String userId = criarUser("João");

        mockMvc.perform(get("/likes/recebidos")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk());
    }
}