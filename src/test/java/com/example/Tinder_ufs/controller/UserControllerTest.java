package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    // Usando o construtor correto: (nome, email, password)
    User USER1 = new User("João", "joao@email.com", "123456");
    User USER2 = new User("Maria", "maria@email.com", "123456");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testUser() throws Exception {
        String id1;
        String id2;

        MvcResult result1 = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(USER1)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson1 = result1.getResponse().getContentAsString();
        User userCriado1 = objectMapper.readValue(responseJson1, User.class);
        id1 = userCriado1.getId();

        MvcResult result2 = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(USER2)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson2 = result2.getResponse().getContentAsString();
        User userCriado2 = objectMapper.readValue(responseJson2, User.class);
        id2 = userCriado2.getId();

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/" + id1))
                .andExpect(status().isOk());

        userCriado1.setNome("João Atualizado");
        mockMvc.perform(put("/users/" + id1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userCriado1)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/users/" + id2))
                .andExpect(status().isNoContent());
    }

    @Test
    void testEmailDuplicado() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(USER1)))
                .andExpect(status().isOk());

        User userDuplicado = new User("João 2", "joao@email.com", "123456");

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userDuplicado)))
                .andExpect(status().is5xxServerError());
    }
}