package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LikeControllerTest {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    User USER = new User("João", "joao@gmail.com", LocalDate.parse("01/01/2001", formatter), "teste123");
    User USER2 = new User("JOANA", "joana@gmail.com", LocalDate.parse("01/01/2001", formatter), "teste123");

    @Autowired
    private MockMvc mockMvc;  // (1)

    @Autowired
    private ObjectMapper objectMapper;  // (2)

    @Test
    void darLike() throws Exception {
         String user1;
         String user2;
        MvcResult result1= mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(USER)))
                .andExpect(status().isOk()) .andReturn();
        String responseJson1 = result1.getResponse().getContentAsString();
        User userCriado1 = objectMapper.readValue(responseJson1, User.class);
        user1 = userCriado1.getId();  // Guardando ID do João
        MvcResult result2= mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(USER2)))
                .andExpect(status().isOk()).andReturn();
        String responseJson2 = result2.getResponse().getContentAsString();
        User userCriado2 = objectMapper.readValue(responseJson2, User.class);
        user2 = userCriado2.getId();  // Guardando ID da Joana
        mockMvc.perform(post("/likes")
                        .param("origemId",user2 )
                        .param("destinoId", user1))
                .andExpect(status().isOk());
        mockMvc.perform(post("/likes")
                        .param("origemId",user1 )
                        .param("destinoId", user2))
                .andExpect(status().isOk());

    }

}