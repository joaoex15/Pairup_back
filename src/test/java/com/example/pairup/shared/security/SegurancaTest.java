package com.example.pairup.shared.security;

import com.example.pairup.user.User;
import com.example.pairup.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Garante as proteções de segurança da API (erros, JWT, cabeçalhos, validação, senha). */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SegurancaTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${jwt.secret}")
    private String secret;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String email() {
        return "seg_" + uid() + "@email.com";
    }

    private String criarUserEToken() throws Exception {
        MvcResult r = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Seg\",\"email\":\"" + email() + "\",\"password\":\"senhaForte1\"}"))
                .andExpect(status().isOk()).andReturn();
        String id = r.getResponse().getContentAsString(StandardCharsets.UTF_8)
                .replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        return "Bearer " + jwtService.generateToken(id);
    }

    // ==================== ERROS ====================

    @Test
    void jsonMalformadoDa400SemDetalhesInternos() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Jackson"))))
                .andExpect(content().string(not(containsString("JSON parse"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void parametroDeTipoInvalidoDa400() throws Exception {
        mockMvc.perform(get("/pessoas").param("genero", "INEXISTENTE")
                        .header("Authorization", criarUserEToken()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    // ==================== JWT ====================

    @Test
    void tokenDeOutroEmissorDa401() throws Exception {
        String token = Jwts.builder()
                .setIssuer("outro-sistema")
                .setSubject("qualquer")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        assertNull(jwtService.extractUserId(token));
    }

    @Test
    void tokenExpiradoOuAdulteradoDa401() throws Exception {
        String valido = criarUserEToken();
        mockMvc.perform(get("/users/me").header("Authorization", valido + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void segredoFracoImpedeASubida() {
        JwtService fraco = new JwtService();
        ReflectionTestUtils.setField(fraco, "secret", "curto");
        assertThrows(IllegalStateException.class, () -> ReflectionTestUtils.invokeMethod(fraco, "init"));
    }

    @Test
    void erroDoControllerNaoViraTokenInvalido() throws Exception {
        // Like em perfil inexistente é erro de negócio (400), não "401 Token inválido"
        mockMvc.perform(post("/likes").param("destinoId", "nao-existe")
                        .header("Authorization", criarUserEToken()))
                .andExpect(status().is4xxClientError())
                .andExpect(status().is(not(401)));
    }

    // ==================== CABEÇALHOS E SESSÃO ====================

    @Test
    void cabecalhosDeSegurancaEsemCookieDeSessao() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", criarUserEToken()).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    // ==================== VALIDAÇÃO ====================

    private String corpoPessoa(String extra) {
        return "{\"nome\":\"Ana\",\"curso\":\"Direito\",\"dataNasc\":\"2000-01-01\",\"genero\":\"FEMININO\","
                + "\"interesse\":\"TODOS\",\"cienciaResponsabilidade\":true" + extra + "}";
    }

    @Test
    void redeSocialComLinkMaliciosoDa400() throws Exception {
        String token = criarUserEToken();
        for (String extra : new String[]{
                ",\"instagram\":\"javascript:alert(1)\"",
                ",\"telegram\":\"<script>\"",
                ",\"whatsapp\":\"abc\""}) {
            mockMvc.perform(post("/pessoas").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(corpoPessoa(extra)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void redeSocialValidaEhAceita() throws Exception {
        mockMvc.perform(post("/pessoas").header("Authorization", criarUserEToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPessoa(",\"instagram\":\"@ana.silva\",\"whatsapp\":\"(79) 99999-0000\",\"telegram\":\"@ana_s\"")))
                .andExpect(status().isOk());
    }

    @Test
    void dataNascFuturaOuImplausivelDa400() throws Exception {
        String token = criarUserEToken();
        mockMvc.perform(post("/pessoas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPessoa("").replace("2000-01-01", "2999-01-01")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/pessoas").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPessoa("").replace("2000-01-01", "1800-01-01")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void campoGiganteDa400() throws Exception {
        mockMvc.perform(post("/pessoas").header("Authorization", criarUserEToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoPessoa("").replace("\"Ana\"", "\"" + "a".repeat(500) + "\"")))
                .andExpect(status().isBadRequest());
    }

    // ==================== SENHA ====================

    @Test
    void cadastroComSenhaDe7CaracteresDa400() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email() + "\",\"password\":\"1234567\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contaAntigaComSenhaDe6CaracteresContinuaEntrando() throws Exception {
        String email = email();
        userRepository.save(new User("Antiga", email, passwordEncoder.encode("123456")));

        mockMvc.perform(post("/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }
}
