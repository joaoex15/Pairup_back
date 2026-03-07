package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class MatchControllerTest {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    User USER1 = new User("João", "joao@gmail.com", LocalDate.parse("01/01/2001", formatter), "teste123");
    User USER2 = new User("Maria", "maria@gmail.com", LocalDate.parse("02/02/2002", formatter), "teste123");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;  // OU ISSO

    @Test
    void listarMeusMatches() throws Exception {
        String user1;
        String user2;

        MvcResult result1 = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(USER1)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson1 = result1.getResponse().getContentAsString();
        User userCriado1 = objectMapper.readValue(responseJson1, User.class);
        user1 = userCriado1.getId();

        MvcResult result2 = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(USER2)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson2 = result2.getResponse().getContentAsString();
        User userCriado2 = objectMapper.readValue(responseJson2, User.class);
        user2 = userCriado2.getId();

        mockMvc.perform(post("/likes")
                        .param("origemId", user1)
                        .param("destinoId", user2))
                .andExpect(status().isOk());

        mockMvc.perform(post("/likes")
                        .param("origemId", user2)
                        .param("destinoId", user1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/matches/" + user1))
                .andExpect(status().isOk());
    }

    @Test
    void desfazerMatch() throws Exception {
        String user1;
        String user2;

        MvcResult result1 = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(USER1)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson1 = result1.getResponse().getContentAsString();
        User userCriado1 = objectMapper.readValue(responseJson1, User.class);
        user1 = userCriado1.getId();

        MvcResult result2 = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(USER2)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson2 = result2.getResponse().getContentAsString();
        User userCriado2 = objectMapper.readValue(responseJson2, User.class);
        user2 = userCriado2.getId();

        mockMvc.perform(post("/likes")
                        .param("origemId", user1)
                        .param("destinoId", user2))
                .andExpect(status().isOk());

        mockMvc.perform(post("/likes")
                        .param("origemId", user2)
                        .param("destinoId", user1))
                .andExpect(status().isOk());

        MvcResult matchResult = mockMvc.perform(get("/matches/" + user1))
                .andExpect(status().isOk())
                .andReturn();

        String matchJson = matchResult.getResponse().getContentAsString();
        String matchId = matchJson.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(put("/matches/desfazer/" + matchId))
                .andExpect(status().isOk());
    }
}