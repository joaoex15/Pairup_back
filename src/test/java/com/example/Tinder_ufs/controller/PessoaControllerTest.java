package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.Pessoa;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
// @Import(TestSecurityConfig.class)  ← REMOVER ESTA LINHA
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PessoaControllerTest {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private User novoUser(String nome) {
        return new User(nome, nome.toLowerCase() + "_" + uid() + "@email.com", "123456");
    }

    private Pessoa novaPessoa(String nome, String email) {
        return new Pessoa(nome, "Engenharia", LocalDate.parse("01/01/2001", formatter), email);
    }

    private String criarUser(String nome) throws Exception {
        User user = novoUser(nome);
        MvcResult r = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(r.getResponse().getContentAsString(), User.class).getId();
    }

    @Test
    void testCriarEListarPessoas() throws Exception {
        String email1 = "joao_" + uid() + "@email.com";
        String email2 = "maria_" + uid() + "@email.com";

        mockMvc.perform(post("/pessoas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("João", email1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pessoas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("Maria", email2))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas"))
                .andExpect(status().isOk());
    }

    @Test
    void testBuscarPerfilPorId() throws Exception {
        String email = "joao_" + uid() + "@email.com";
        MvcResult result = mockMvc.perform(post("/pessoas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("João", email))))
                .andExpect(status().isOk())
                .andReturn();

        Pessoa criada = objectMapper.readValue(result.getResponse().getContentAsString(), Pessoa.class);

        mockMvc.perform(get("/pessoas/" + criada.getId() + "/perfil"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetMeuPerfil() throws Exception {
        String userId = criarUser("João");

        mockMvc.perform(get("/pessoas/me")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void testGetMeuPerfilSemUserIdDeveRetornar401() throws Exception {
        mockMvc.perform(get("/pessoas/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAtualizarMeuPerfil() throws Exception {
        String userId = criarUser("João");
        String email = "joao_" + uid() + "@email.com";
        Pessoa pessoa = novaPessoa("João Atualizado", email);

        mockMvc.perform(put("/pessoas/me")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pessoa)))
                .andExpect(status().isOk());
    }

    @Test
    void testMarcarCienciaResponsabilidade() throws Exception {
        String userId = criarUser("João");

        mockMvc.perform(patch("/pessoas/me/ciencia-responsabilidade")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void testDeletarMeuPerfil() throws Exception {
        String userId = criarUser("Maria");

        mockMvc.perform(delete("/pessoas/me")
                        .with(comUsuario(userId)))
                .andExpect(status().isNoContent());
    }

    @Test
    void testRedesSociaisDeveLancarExcecao() throws Exception {
        String email = "joao_" + uid() + "@email.com";
        MvcResult result = mockMvc.perform(post("/pessoas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("João", email))))
                .andExpect(status().isOk())
                .andReturn();
        Pessoa criada = objectMapper.readValue(result.getResponse().getContentAsString(), Pessoa.class);

        mockMvc.perform(get("/pessoas/" + criada.getId() + "/redes-sociais"))
                .andExpect(status().is5xxServerError());
    }
}