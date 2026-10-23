package com.example.pairup.pessoa;

import com.example.pairup.imagem.Imagem;
import com.example.pairup.imagem.ImagemRepository;
import com.example.pairup.tag.Tag;
import com.example.pairup.tag.TagRepository;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PessoaControllerTest {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private PessoaRepository pessoaRepository;

    @Autowired
    private ImagemRepository imagemRepository;

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

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private User novoUser(String nome) {
        return new User(nome, nome.toLowerCase() + "_" + uid() + "@email.com", "123456");
    }

    private Pessoa novaPessoa(String nome, String email) {
        Pessoa pessoa = new Pessoa(nome, "Engenharia", LocalDate.parse("01/01/2001", formatter), email);
        // ✅ POST /pessoas exige aceite dos termos (PessoaController.create) e
        // genero/interesse fazem parte do domínio real de Pessoa.
        pessoa.setGenero(Genero.OUTRO);
        pessoa.setInteresse(Interesse.TODOS);
        pessoa.setCienciaResponsabilidade(true);
        return pessoa;
    }

    /** Corpo de POST/PUT /pessoas como Map, para incluir campos que a entidade não serializa como o front envia (tags = IDs). */
    private Map<String, Object> corpoPessoa(String nome, String email) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", nome);
        corpo.put("curso", "Engenharia");
        corpo.put("dataNasc", "2001-01-01");
        corpo.put("email", email);
        corpo.put("genero", "OUTRO");
        corpo.put("interesse", "TODOS");
        corpo.put("cienciaResponsabilidade", true);
        return corpo;
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
        // ✅ POST /pessoas cria o perfil do usuário autenticado — cada perfil
        // precisa de um usuário diferente (um usuário só pode ter um perfil).
        String userId1 = criarUser("João");
        String userId2 = criarUser("Maria");
        String email1 = "joao_" + uid() + "@email.com";
        String email2 = "maria_" + uid() + "@email.com";

        mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("João", email1))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("Maria", email2))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas")
                        .with(comUsuario(userId1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].nome").exists())
                .andExpect(jsonPath("$.content[0].tags").isArray())
                .andExpect(jsonPath("$.content[0].imagens").isArray());
    }

    @Test
    void testBuscarPerfilPorId() throws Exception {
        String userId = criarUser("João");
        String email = "joao_" + uid() + "@email.com";
        MvcResult result = mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("João", email))))
                .andExpect(status().isOk())
                .andReturn();

        Pessoa criada = objectMapper.readValue(result.getResponse().getContentAsString(), Pessoa.class);

        // Perfil público: dados básicos, sem redes sociais (essas só via match).
        mockMvc.perform(get("/pessoas/" + criada.getId() + "/perfil")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(criada.getId()))
                .andExpect(jsonPath("$.nome").value("João"))
                .andExpect(jsonPath("$.curso").value("Engenharia"))
                .andExpect(jsonPath("$.dataNasc").value("2001-01-01"))
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.imagens").isArray())
                .andExpect(jsonPath("$.instagram").doesNotExist())
                .andExpect(jsonPath("$.whatsapp").doesNotExist());
    }

    @Test
    void testGetMeuPerfil() throws Exception {
        String userId = criarUser("João");
        String email = "joao_" + uid() + "@email.com";
        mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("João", email))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/me")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("João"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.dataNasc").value("2001-01-01"))
                .andExpect(jsonPath("$.cienciaResponsabilidade").value(true))
                .andExpect(jsonPath("$.perfilCompleto").value(true))
                .andExpect(jsonPath("$.quantidadeGaleria").value(0))
                .andExpect(jsonPath("$.imagens").isArray())
                .andExpect(jsonPath("$.tags").isArray());
    }

    @Test
    void testGetMeuPerfilSemUserIdDeveRetornar401() throws Exception {
        mockMvc.perform(get("/pessoas/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAtualizarMeuPerfil() throws Exception {
        String userId = criarUser("João");
        String emailOriginal = "joao_" + uid() + "@email.com";
        mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("João", emailOriginal))))
                .andExpect(status().isOk());

        String email = "joao_" + uid() + "@email.com";
        Pessoa pessoa = novaPessoa("João Atualizado", email);

        mockMvc.perform(put("/pessoas/me")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pessoa)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/me")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("João Atualizado"));
    }

    @Test
    void testMarcarCienciaResponsabilidade() throws Exception {
        String userId = criarUser("João");
        String email = "joao_" + uid() + "@email.com";
        mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("João", email))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/pessoas/me/ciencia-responsabilidade")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void testDeletarMeuPerfil() throws Exception {
        String userId = criarUser("Maria");
        String email = "maria_" + uid() + "@email.com";
        mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("Maria", email))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/pessoas/me")
                        .with(comUsuario(userId)))
                .andExpect(status().isNoContent());
    }

    @Test
    void testRedesSociaisSemMatchDeveRetornar403() throws Exception {
        String userId = criarUser("João");
        String email = "joao_" + uid() + "@email.com";
        MvcResult result = mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(novaPessoa("João", email))))
                .andExpect(status().isOk())
                .andReturn();
        Pessoa criada = objectMapper.readValue(result.getResponse().getContentAsString(), Pessoa.class);

        // ✅ GET /pessoas/{id}/redes-sociais é um stub intencional que sempre nega
        // acesso direto (ver docs/ARQUITETURA.md, item "Endpoint stub que sempre
        // retorna 403") — o caminho correto é via /matches/{matchId}/redes-sociais.
        // A rota exige autenticação (SecurityConfig: "/pessoas/**").
        mockMvc.perform(get("/pessoas/" + criada.getId() + "/redes-sociais")
                        .with(comUsuario(userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testRedesSociaisETagsSaoSalvasEAtualizadas() throws Exception {
        Tag tag = tagRepository.save(new Tag("Música_" + uid(), null, "Hobbies", true));
        String userId = criarUser("João");

        Map<String, Object> corpo = corpoPessoa("João", "joao_" + uid() + "@email.com");
        corpo.put("instagram", "@joao");
        corpo.put("whatsapp", "(79) 99999-0000");
        corpo.put("tags", List.of(tag.getId()));

        mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/me")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagram").value("@joao"))
                .andExpect(jsonPath("$.whatsapp").value("(79) 99999-0000"))
                .andExpect(jsonPath("$.telegram").doesNotExist())
                .andExpect(jsonPath("$.tags[0]").value(tag.getNome()));

        // String vazia apaga a rede social; lista vazia remove as tags; campo ausente mantém
        Map<String, Object> atualizacao = new HashMap<>();
        atualizacao.put("instagram", "");
        atualizacao.put("tags", List.of());

        mockMvc.perform(put("/pessoas/me")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(atualizacao)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/pessoas/me")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instagram").doesNotExist())
                .andExpect(jsonPath("$.whatsapp").value("(79) 99999-0000"))
                .andExpect(jsonPath("$.tags").isEmpty());
    }

    @Test
    void testTagInexistenteDeveRetornar400() throws Exception {
        String userId = criarUser("João");
        Map<String, Object> corpo = corpoPessoa("João", "joao_" + uid() + "@email.com");
        corpo.put("tags", List.of("tag-que-nao-existe"));

        mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testImagensDaListagemNaoExpoemDadosDaPessoa() throws Exception {
        String donoId = criarUser("Maria");
        Map<String, Object> corpo = corpoPessoa("Maria", "maria_" + uid() + "@email.com");
        corpo.put("instagram", "@maria_secreta");
        mockMvc.perform(post("/pessoas")
                        .with(comUsuario(donoId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andExpect(status().isOk());

        // Imagem gravada direto no banco (sem passar pelo R2/MinIO)
        Pessoa dona = pessoaRepository.findByUsuarioId(donoId).orElseThrow();
        String key = "tinder_ufs/" + dona.getId() + "/" + uid() + ".jpg";
        imagemRepository.save(new Imagem(dona, "http://localhost:9000/test-bucket/" + key,
                key, "tinder_ufs/" + dona.getId(), true, 10, "image/jpeg"));

        String visitanteId = criarUser("João");
        mockMvc.perform(post("/pessoas")
                        .with(comUsuario(visitanteId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpoPessoa("João", "joao_" + uid() + "@email.com"))))
                .andExpect(status().isOk());

        // Redes sociais só via match: nem no perfil público, nem dentro de imagens[]
        mockMvc.perform(get("/pessoas/" + dona.getId() + "/perfil")
                        .with(comUsuario(visitanteId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imagens[0].url").exists())
                .andExpect(jsonPath("$.imagens[0].pessoa").doesNotExist())
                .andExpect(content().string(not(containsString("@maria_secreta"))));

        mockMvc.perform(get("/pessoas")
                        .param("size", "100")
                        .with(comUsuario(visitanteId)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("@maria_secreta"))));
    }
}
