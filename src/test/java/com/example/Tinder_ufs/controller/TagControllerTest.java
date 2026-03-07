package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class TagControllerTest {

    Tag TAG1 = new Tag("Esporte", "Tag para esportes", "Lazer", true);
    Tag TAG2 = new Tag("Música", "Tag para música", "Cultura", true);
    Tag TAG3 = new Tag("Viagem", "Tag para viagens", "Lazer", false);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testTag() throws Exception {
        String id1;
        String id2;
        String id3;

        // Criar tags
        MvcResult result1 = mockMvc.perform(post("/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TAG1)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson1 = result1.getResponse().getContentAsString();
        Tag tagCriada1 = objectMapper.readValue(responseJson1, Tag.class);
        id1 = tagCriada1.getId();

        MvcResult result2 = mockMvc.perform(post("/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TAG2)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson2 = result2.getResponse().getContentAsString();
        Tag tagCriada2 = objectMapper.readValue(responseJson2, Tag.class);
        id2 = tagCriada2.getId();

        MvcResult result3 = mockMvc.perform(post("/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TAG3)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson3 = result3.getResponse().getContentAsString();
        Tag tagCriada3 = objectMapper.readValue(responseJson3, Tag.class);
        id3 = tagCriada3.getId();

        // Listar todas
        mockMvc.perform(get("/tags"))
                .andExpect(status().isOk());

        // Listar apenas ativas
        mockMvc.perform(get("/tags/ativas"))
                .andExpect(status().isOk());

        // Deletar
        mockMvc.perform(delete("/tags/" + id3))
                .andExpect(status().isNoContent());
    }
}