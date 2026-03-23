package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Pessoa;
import com.fasterxml.jackson.databind.ObjectMapper;  // ✅ CORRETO
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;  // ✅ CORRETO
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc  // ✅ Agora funciona!
class PessoaControllerTest {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    Pessoa PESSOA1 = new Pessoa("João", "Engenharia", LocalDate.parse("01/01/2001", formatter),  "joao@email.com");
    Pessoa PESSOA2 = new Pessoa("Maria", "Medicina", LocalDate.parse("02/02/2002", formatter),  "maria@email.com");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;  // ✅ Funciona!

    @Test
    void testPessoa() throws Exception {
        String id1;
        String id2;

        MvcResult result1 = mockMvc.perform(post("/pessoas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PESSOA1)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson1 = result1.getResponse().getContentAsString();
        Pessoa pessoaCriada1 = objectMapper.readValue(responseJson1, Pessoa.class);
        id1 = pessoaCriada1.getId();

        MvcResult result2 = mockMvc.perform(post("/pessoas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PESSOA2)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson2 = result2.getResponse().getContentAsString();
        Pessoa pessoaCriada2 = objectMapper.readValue(responseJson2, Pessoa.class);
        id2 = pessoaCriada2.getId();

        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/" + id1))
                .andExpect(status().isOk());

        pessoaCriada1.setNome("João Silva");
        mockMvc.perform(put("/pessoas/" + id1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pessoaCriada1)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/pessoas/" + id2))
                .andExpect(status().isNoContent());
    }
}