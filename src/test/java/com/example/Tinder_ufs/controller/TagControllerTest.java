package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Tag;
import com.fasterxml.jackson.databind.ObjectMapper;  // ✅ CORRETO
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;  // ✅ CORRETO
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc  // ✅ Agora funciona!
class TagControllerTest {

    Tag TAG1 = new Tag("Esporte", "Tag para esportes", "Lazer", true);
    Tag TAG2 = new Tag("Música", "Tag para música", "Cultura", true);
    Tag TAG3 = new Tag("Viagem", "Tag para viagens", "Lazer", false);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;  // ✅ Funciona!

    @Test
    void testTag() throws Exception {
        String id1;
        String id2;
        String id3;

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

        mockMvc.perform(get("/tags"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tags/ativas"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/tags/" + id3))
                .andExpect(status().isNoContent());
    }
}