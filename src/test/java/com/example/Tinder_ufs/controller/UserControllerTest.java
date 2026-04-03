package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.dto.LoginRequestDTO;
import com.example.Tinder_ufs.models.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String authToken;
    private String userId;
    private String testEmail;

    private User novoUser(String nome) {
        String email = nome.toLowerCase().replaceAll(" ", "_")
                + "_" + UUID.randomUUID().toString().substring(0, 8) + "@email.com";
        return new User(nome, email, "123456");
    }

    @BeforeEach
    void setUp() throws Exception {
        User user = novoUser("Teste");
        testEmail = user.getEmail();

        // Criar usuário
        MvcResult createResult = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andReturn();

        User userCriado = objectMapper.readValue(createResult.getResponse().getContentAsString(), User.class);
        userId = userCriado.getId();

        // Login para obter token real
        LoginRequestDTO loginRequest = new LoginRequestDTO();
        loginRequest.setEmail(testEmail);
        loginRequest.setPassword("123456");

        MvcResult loginResult = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, String> tokenMap = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        authToken = tokenMap.get("token");
    }

    @Test
    void testCriarUsuario() throws Exception {
        User user = novoUser("João");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("João"));
    }

    @Test
    void testGetMe() throws Exception {
        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.email").value(testEmail));
    }

    @Test
    void testUpdateMe() throws Exception {
        User userAtualizado = new User();
        userAtualizado.setNome("João Atualizado");
        userAtualizado.setEmail(testEmail);

        mockMvc.perform(put("/users/me")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userAtualizado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("João Atualizado"));
    }

    @Test
    void testEmailDuplicado() throws Exception {
        User user1 = novoUser("João");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user1)))
                .andExpect(status().isOk());

        User userDuplicado = new User("João 2", user1.getEmail(), "123456");
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDuplicado)))
                .andExpect(status().isConflict());
    }

    @Test
    void testAcessoSemToken() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDeleteMe() throws Exception {
        User tempUser = novoUser("Temp");

        MvcResult createResult = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tempUser)))
                .andExpect(status().isOk())
                .andReturn();

        User tempCriado = objectMapper.readValue(createResult.getResponse().getContentAsString(), User.class);

        LoginRequestDTO loginTemp = new LoginRequestDTO();
        loginTemp.setEmail(tempUser.getEmail());
        loginTemp.setPassword("123456");

        MvcResult loginResult = mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginTemp)))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        Map<String, String> tokenMap = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        String tempToken = tokenMap.get("token");

        mockMvc.perform(delete("/users/me")
                        .header("Authorization", "Bearer " + tempToken))
                .andExpect(status().isNoContent());
    }
}