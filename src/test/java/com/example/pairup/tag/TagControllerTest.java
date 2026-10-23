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
        User user = new User("Usuário Tag", email, "123456");
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

    @Test
    void testCriarEListarTags() throws Exception {
        String userId = criarUser();

        mockMvc.perform(post("/tags")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaTag("Esporte", "Lazer", true))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tags")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaTag("Música", "Cultura", true))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tags")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaTag("Viagem", "Lazer", false))))
                .andExpect(status().isOk());

        // GET /tags e /tags/ativas são públicos — não exigem autenticação.
        mockMvc.perform(get("/tags"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tags/ativas"))
                .andExpect(status().isOk());
    }

    @Test
    void testDeleteTagDeveSerBloqueado() throws Exception {
        String userId = criarUser();

        MvcResult result = mockMvc.perform(post("/tags")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaTag("Esporte", "Lazer", true))))
                .andExpect(status().isOk())
                .andReturn();

        Tag tagCriada = objectMapper.readValue(result.getResponse().getContentAsString(), Tag.class);

        // ✅ DELETE /tags/{id} exige role ADMIN (SecurityConfig: hasRole("ADMIN")).
        // Como nenhum usuário jamais recebe essa role (CustomUserDetailsService
        // sempre monta authorities vazias — ver docs/ARQUITETURA.md, item 4), o
        // Spring Security barra a requisição antes mesmo de chegar no controller,
        // respondendo 403 (não o 5xx que este teste esperava antes, nem o JSON
        // customizado do TagController.delete, que é código morto/inatingível).
        mockMvc.perform(delete("/tags/" + tagCriada.getId())
                        .with(comUsuario(userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCriarTagComDadosInvalidos() throws Exception {
        String userId = criarUser();

        mockMvc.perform(post("/tags")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
