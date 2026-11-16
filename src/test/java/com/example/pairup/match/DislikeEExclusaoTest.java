package com.example.pairup.match;

import com.example.pairup.pessoa.Genero;
import com.example.pairup.pessoa.Interesse;
import com.example.pairup.pessoa.Pessoa;
import com.example.pairup.pessoa.PessoaRepository;
import com.example.pairup.shared.security.JwtService;
import com.example.pairup.user.User;
import com.example.pairup.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** "Passar" salvo no back e exclusão de perfil/conta em cascata. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DislikeEExclusaoTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private LikeRepository likeRepository;
    @Autowired private DislikeRepository dislikeRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private PessoaRepository pessoaRepository;
    @Autowired private UserRepository userRepository;

    private RequestPostProcessor comUsuario(String userId) {
        String token = jwtService.generateToken(userId);
        return (MockHttpServletRequest request) -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private String criarUser(String nome) throws Exception {
        String email = nome.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        MvcResult r = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new User(nome, email, "teste123"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(r.getResponse().getContentAsString(), User.class).getId();
    }

    private String criarPessoa(String userId, String nome) throws Exception {
        String email = nome.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8) + "@email.com";
        Pessoa pessoa = new Pessoa(nome, "Engenharia", LocalDate.of(2000, 1, 1), email);
        pessoa.setGenero(Genero.OUTRO);
        pessoa.setInteresse(Interesse.TODOS);
        pessoa.setCienciaResponsabilidade(true);
        MvcResult r = mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pessoa)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(r.getResponse().getContentAsString(), Pessoa.class).getId();
    }

    @Test
    void dislikeFicaSalvoEAparaceNaLista() throws Exception {
        String u1 = criarUser("Ana");
        criarPessoa(u1, "Ana");
        String u2 = criarUser("Bia");
        String p2 = criarPessoa(u2, "Bia");

        mockMvc.perform(post("/dislikes").with(comUsuario(u1)).param("destinoId", p2))
                .andExpect(status().isNoContent());
        // Repetir não é erro nem duplica
        mockMvc.perform(post("/dislikes").with(comUsuario(u1)).param("destinoId", p2))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/dislikes/dados").with(comUsuario(u1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].pessoaDestinoId").value(p2));
    }

    @Test
    void dislikeEmSiMesmoOuPerfilInexistenteDeveRetornar400() throws Exception {
        String u1 = criarUser("Ana");
        String p1 = criarPessoa(u1, "Ana");

        mockMvc.perform(post("/dislikes").with(comUsuario(u1)).param("destinoId", p1))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/dislikes").with(comUsuario(u1)).param("destinoId", "nao-existe"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apagarPerfilApagaLikesDislikesEMatches() throws Exception {
        String u1 = criarUser("Ana");
        String p1 = criarPessoa(u1, "Ana");
        String u2 = criarUser("Bia");
        String p2 = criarPessoa(u2, "Bia");
        String u3 = criarUser("Caio");
        String p3 = criarPessoa(u3, "Caio");

        mockMvc.perform(post("/likes").with(comUsuario(u1)).param("destinoId", p2)).andExpect(status().isOk());
        mockMvc.perform(post("/likes").with(comUsuario(u2)).param("destinoId", p1))
                .andExpect(jsonPath("$.match").value(true));
        mockMvc.perform(post("/dislikes").with(comUsuario(u3)).param("destinoId", p1)).andExpect(status().isNoContent());

        mockMvc.perform(delete("/pessoas/me").with(comUsuario(u1))).andExpect(status().isNoContent());

        assertThat(pessoaRepository.findById(p1)).isEmpty();
        assertThat(likeRepository.findAll()).noneMatch(l -> p1.equals(l.getPessoaOrigemId()) || p1.equals(l.getPessoaDestinoId()));
        assertThat(dislikeRepository.findAll()).noneMatch(d -> p1.equals(d.getPessoaDestinoId()));
        assertThat(matchRepository.findAll()).noneMatch(m -> p1.equals(m.getPessoaId1()) || p1.equals(m.getPessoaId2()));

        // A outra pessoa não vê mais o match
        mockMvc.perform(get("/matches/meus").with(comUsuario(u2)))
                .andExpect(jsonPath("$.totalElements").value(0));
        // Quem não tinha relação com o perfil apagado não é afetado
        assertThat(pessoaRepository.findById(p3)).isPresent();
    }

    @Test
    void apagarContaApagaOPerfil() throws Exception {
        String u1 = criarUser("Ana");
        String p1 = criarPessoa(u1, "Ana");

        mockMvc.perform(delete("/users/me").with(comUsuario(u1))).andExpect(status().isNoContent());

        assertThat(userRepository.findById(u1)).isEmpty();
        assertThat(pessoaRepository.findById(p1)).isEmpty();
    }
}
