package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Match;
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

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
// @Import(TestSecurityConfig.class)  ← REMOVER ESTA LINHA
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MatchControllerTest {

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

    private String[] criarUsersEGerarMatch() throws Exception {
        String user1Id = criarUser("João");
        String user2Id = criarUser("Maria");

        mockMvc.perform(post("/likes")
                        .with(comUsuario(user1Id))
                        .param("destinoId", user2Id))
                .andExpect(status().isOk());

        mockMvc.perform(post("/likes")
                        .with(comUsuario(user2Id))
                        .param("destinoId", user1Id))
                .andExpect(status().isOk());

        return new String[]{user1Id, user2Id};
    }

    @Test
    void listarMeusMatches() throws Exception {
        String[] ids = criarUsersEGerarMatch();

        mockMvc.perform(get("/matches/meus")
                        .with(comUsuario(ids[0])))
                .andExpect(status().isOk());
    }

    @Test
    void listarMatchesSemUserIdDeveRetornar401() throws Exception {
        mockMvc.perform(get("/matches/meus"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void desfazerMatch() throws Exception {
        String[] ids = criarUsersEGerarMatch();
        String user1Id = ids[0];

        MvcResult matchResult = mockMvc.perform(get("/matches/meus")
                        .with(comUsuario(user1Id)))
                .andExpect(status().isOk())
                .andReturn();

        List<Match> matches = objectMapper.readValue(
                matchResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Match.class));
        String matchId = matches.get(0).getId();

        mockMvc.perform(put("/matches/desfazer/" + matchId)
                        .with(comUsuario(user1Id)))
                .andExpect(status().isOk());
    }

    @Test
    void desfazerMatchDeOutroUsuarioDeveRetornar403() throws Exception {
        String[] ids = criarUsersEGerarMatch();
        String user1Id = ids[0];

        MvcResult matchResult = mockMvc.perform(get("/matches/meus")
                        .with(comUsuario(user1Id)))
                .andExpect(status().isOk())
                .andReturn();

        List<Match> matches = objectMapper.readValue(
                matchResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Match.class));
        String matchId = matches.get(0).getId();

        String user3Id = criarUser("Terceiro");
        mockMvc.perform(put("/matches/desfazer/" + matchId)
                        .with(comUsuario(user3Id)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRedesSociaisDoMatch() throws Exception {
        String[] ids = criarUsersEGerarMatch();
        String user1Id = ids[0];

        MvcResult matchResult = mockMvc.perform(get("/matches/meus")
                        .with(comUsuario(user1Id)))
                .andExpect(status().isOk())
                .andReturn();

        List<Match> matches = objectMapper.readValue(
                matchResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Match.class));
        String matchId = matches.get(0).getId();

        mockMvc.perform(get("/matches/" + matchId + "/redes-sociais")
                        .with(comUsuario(user1Id)))
                .andExpect(status().isOk());
    }
}