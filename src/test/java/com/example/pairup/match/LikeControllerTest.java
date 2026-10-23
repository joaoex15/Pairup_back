package com.example.pairup.match;

import com.example.pairup.pessoa.Pessoa;
import com.example.pairup.user.User;
import com.example.pairup.pessoa.Genero;
import com.example.pairup.pessoa.Interesse;
import com.example.pairup.shared.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    /**
     * ✅ Autentica de verdade: gera um JWT real (mesmo mecanismo usado em produção,
     * via JwtService) e o envia no header Authorization, para que a requisição
     * passe pelo JwtFilter real (que não lê request.setAttribute("userId", ...)).
     */
    private RequestPostProcessor comUsuario(String userId) {
        String token = jwtService.generateToken(userId);
        return (MockHttpServletRequest request) -> {
            request.addHeader("Authorization", "Bearer " + token);
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

    /**
     * ✅ LikeController traduz User.id -> Pessoa.id via pessoaService.findByUsuarioId
     * antes de operar (ver docs/ARQUITETURA.md, item crítico 1) — um perfil precisa
     * existir para o usuário poder dar/listar likes. Retorna o Pessoa.id criado.
     */
    private String criarPessoaParaUsuario(String userId, String nome) throws Exception {
        String email = nome.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8) + "@email.com";
        Pessoa pessoa = new Pessoa(nome, "Engenharia", LocalDate.of(2000, 1, 1), email);
        pessoa.setGenero(Genero.OUTRO);
        pessoa.setInteresse(Interesse.TODOS);
        pessoa.setCienciaResponsabilidade(true);

        MvcResult r = mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pessoa)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(r.getResponse().getContentAsString(), Pessoa.class).getId();
    }

    @Test
    void darLike() throws Exception {
        String user1Id = criarUser("João");
        String pessoa1Id = criarPessoaParaUsuario(user1Id, "João");
        String user2Id = criarUser("Joana");
        String pessoa2Id = criarPessoaParaUsuario(user2Id, "Joana");

        mockMvc.perform(post("/likes")
                        .with(comUsuario(user2Id))
                        .param("destinoId", pessoa1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match").value(false));

        // Like recíproco vira match.
        mockMvc.perform(post("/likes")
                        .with(comUsuario(user1Id))
                        .param("destinoId", pessoa2Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match").value(true));
    }

    @Test
    void darLikeEmSimMesmoDeveRetornar400() throws Exception {
        String userId = criarUser("João");
        String pessoaId = criarPessoaParaUsuario(userId, "João");

        mockMvc.perform(post("/likes")
                        .with(comUsuario(userId))
                        .param("destinoId", pessoaId))
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
        criarPessoaParaUsuario(userId, "João");

        mockMvc.perform(get("/likes/dados")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void listarLikesRecebidos() throws Exception {
        String userId = criarUser("João");
        criarPessoaParaUsuario(userId, "João");

        mockMvc.perform(get("/likes/recebidos")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk());
    }
}
