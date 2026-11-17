package com.example.pairup.tag;

import com.example.pairup.user.User;
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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    /**
     * ✅ Autentica de verdade: gera um JWT real (mesmo mecanismo usado em produção,
     * via JwtService) e o envia no header Authorization, para que a requisição
     * passe pelo JwtFilter real. POST /tags exige autenticação
     * (SecurityConfig: "/tags" -> authenticated()).
     */
    private RequestPostProcessor comUsuario(String userId) {
        String token = jwtService.generateToken(userId);
        return (MockHttpServletRequest request) -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private String criarUser() throws Exception {
        String email = "tag_" + UUID.randomUUID().toString().substring(0, 8) + "@email.com";
        User user = new User("Usuário Tag", email, "12345678");
        MvcResult r = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(r.getResponse().getContentAsString(), User.class).getId();
    }

    private Tag novaTag(String nome, String categoria, boolean ativa) {
        return new Tag(nome + "_" + UUID.randomUUID().toString().substring(0, 6),
                "Descrição de " + nome, categoria, ativa);
    }

    @Autowired
    private TagRepository tagRepository;

    @Test
    void testListarTagsEhPublico() throws Exception {
        tagRepository.save(novaTag("Esporte", "Lazer", true));

        // GET /tags e /tags/ativas são públicos — não exigem autenticação.
        mockMvc.perform(get("/tags"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tags/ativas"))
                .andExpect(status().isOk());
    }

    /** POST /tags exige ADMIN: usuário comum não cria tags. */
    @Test
    void testUsuarioComumNaoCriaTag() throws Exception {
        String userId = criarUser();

        mockMvc.perform(post("/tags")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaTag("Música", "Cultura", true))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaTag("Música", "Cultura", true))))
                .andExpect(status().isUnauthorized());
    }

    /** Antes, enviar o id de uma tag existente no POST sobrescrevia a tag de todo mundo. */
    @Test
    void testUsuarioComumNaoSobrescreveTagExistente() throws Exception {
        String userId = criarUser();
        Tag existente = tagRepository.save(novaTag("Viagem", "Lazer", true));

        Tag ataque = new Tag("hackeada", "x", "x", false);
        ataque.setId(existente.getId());

        mockMvc.perform(post("/tags")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ataque)))
                .andExpect(status().isForbidden());

        Tag depois = tagRepository.findById(existente.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(existente.getNome(), depois.getNome());
        org.junit.jupiter.api.Assertions.assertTrue(depois.isAtiva());
    }

    @Test
    void testDeleteTagDeveSerBloqueado() throws Exception {
        String userId = criarUser();
        Tag tag = tagRepository.save(novaTag("Esporte", "Lazer", true));

        // DELETE /tags/{id} exige role ADMIN, que nenhum usuário recebe hoje.
        mockMvc.perform(delete("/tags/" + tag.getId())
                        .with(comUsuario(userId)))
                .andExpect(status().isForbidden());
    }
}
