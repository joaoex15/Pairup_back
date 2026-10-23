package com.example.pairup.match;

import com.example.pairup.pessoa.Pessoa;
import com.example.pairup.user.User;
import com.example.pairup.pessoa.Genero;
import com.example.pairup.pessoa.Interesse;
import com.example.pairup.shared.security.JwtService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MatchControllerTest {

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
     * ✅ MatchController traduz User.id -> Pessoa.id via pessoaService.findByUsuarioId
     * antes de operar (ver docs/ARQUITETURA.md, item crítico 1) — um perfil precisa
     * existir para o usuário poder ter likes/matches. Retorna o Pessoa.id criado.
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

    private String[] criarUsersEGerarMatch() throws Exception {
        String user1Id = criarUser("João");
        String pessoa1Id = criarPessoaParaUsuario(user1Id, "João");
        String user2Id = criarUser("Maria");
        String pessoa2Id = criarPessoaParaUsuario(user2Id, "Maria");

        mockMvc.perform(post("/likes")
                        .with(comUsuario(user1Id))
                        .param("destinoId", pessoa2Id))
                .andExpect(status().isOk());

        mockMvc.perform(post("/likes")
                        .with(comUsuario(user2Id))
                        .param("destinoId", pessoa1Id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.match").value(true));

        return new String[]{user1Id, user2Id, pessoa1Id, pessoa2Id};
    }

    /**
     * ✅ GET /matches/meus é paginado (MatchController.listarMeusMatches devolve
     * Page<Match>, não uma lista simples) — o JSON tem a forma
     * {"content": [...], "pageable": {...}, ...}. Extrai apenas o array "content".
     */
    private List<Match> extrairMatches(MvcResult matchResult) throws Exception {
        JsonNode root = objectMapper.readTree(matchResult.getResponse().getContentAsString());
        return objectMapper.convertValue(root.get("content"), new TypeReference<List<Match>>() {});
    }

    @Test
    void listarMeusMatches() throws Exception {
        String[] ids = criarUsersEGerarMatch();

        mockMvc.perform(get("/matches/meus")
                        .with(comUsuario(ids[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        // O match aparece para os dois lados.
        mockMvc.perform(get("/matches/meus")
                        .with(comUsuario(ids[1])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
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

        List<Match> matches = extrairMatches(matchResult);
        String matchId = matches.get(0).getId();

        mockMvc.perform(put("/matches/desfazer/" + matchId)
                        .with(comUsuario(user1Id)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/matches/meus")
                        .with(comUsuario(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void desfazerMatchDeOutroUsuarioDeveRetornar403() throws Exception {
        String[] ids = criarUsersEGerarMatch();
        String user1Id = ids[0];

        MvcResult matchResult = mockMvc.perform(get("/matches/meus")
                        .with(comUsuario(user1Id)))
                .andExpect(status().isOk())
                .andReturn();

        List<Match> matches = extrairMatches(matchResult);
        String matchId = matches.get(0).getId();

        // ✅ Precisa ter perfil (Pessoa) para passar da tradução User.id -> Pessoa.id
        // em MatchController.resolvePessoaId; sem perfil o retorno seria 400 (perfil
        // não encontrado), não 403 (não faz parte do match) — que é o que este teste
        // quer provar.
        String user3Id = criarUser("Terceiro");
        criarPessoaParaUsuario(user3Id, "Terceiro");

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

        List<Match> matches = extrairMatches(matchResult);
        String matchId = matches.get(0).getId();

        // Retorna as redes da OUTRA pessoa do match.
        mockMvc.perform(get("/matches/" + matchId + "/redes-sociais")
                        .with(comUsuario(user1Id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ids[3]));
    }
}
