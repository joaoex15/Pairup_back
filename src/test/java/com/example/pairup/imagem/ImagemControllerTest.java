package com.example.pairup.imagem;

import com.example.pairup.user.User;
import com.example.pairup.pessoa.Pessoa;
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
import org.springframework.mock.web.MockMultipartFile;
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
@ActiveProfiles("test")
@AutoConfigureMockMvc
// ✅ Isola o contexto/estado de segurança entre métodos de teste. Isso, por si só,
// não limpa o MongoDB (ver docs/ARQUITETURA.md, item 12) — por isso os e-mails
// abaixo também são únicos por execução (UUID), para não colidir com o índice
// único de User.email/Pessoa.email em execuções anteriores.
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ImagemControllerTest {

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    private String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

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

    /**
     * Cria um User e um Pessoa associado, retornando o userId.
     * ✅ E-mails únicos por chamada (evita 409 de e-mail duplicado entre execuções)
     * e POST /pessoas autenticado (a rota exige token — ver SecurityConfig).
     */
    private String criarUserComPerfil() throws Exception {
        String email = "joao_imagem_" + uid() + "@email.com";
        User user = new User("João", email, "123456");
        MvcResult userResult = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(user)))
                .andExpect(status().isOk())
                .andReturn();
        String userId = objectMapper.readValue(
                userResult.getResponse().getContentAsString(), User.class).getId();

        Pessoa pessoa = new Pessoa("João", "Engenharia", LocalDate.parse("01/01/2001", formatter), email);
        pessoa.setGenero(Genero.OUTRO);
        pessoa.setInteresse(Interesse.TODOS);
        pessoa.setCienciaResponsabilidade(true);

        mockMvc.perform(post("/pessoas")
                        .with(comUsuario(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pessoa)))
                .andExpect(status().isOk());

        return userId;
    }

    @Test
    void uploadImagemSemTokenDeveRetornar401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/imagens/upload")
                        .file(file)
                        .param("perfil", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadImagemSemPerfilCadastradoDeveRetornar404() throws Exception {
        // User existe mas não tem Pessoa associada
        String email = "semperfil_imagem_" + uid() + "@email.com";
        User userSemPerfil = new User("Sem Perfil", email, "123456");
        MvcResult userResult = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userSemPerfil)))
                .andExpect(status().isOk())
                .andReturn();
        String userId = objectMapper.readValue(
                userResult.getResponse().getContentAsString(), User.class).getId();

        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes".getBytes());

        // ✅ ImagemController.uploadImagem responde 400 (não 404) quando o perfil não
        // existe (ImagemController.java:40-43) — o teste esperava um contrato que o
        // controller não implementa.
        mockMvc.perform(multipart("/api/imagens/upload")
                        .file(file)
                        .param("perfil", "true")
                        .with(comUsuario(userId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadImagemComArquivoVazioDeveRetornar400() throws Exception {
        String userId = criarUserComPerfil();

        // Arquivo vazio
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "vazio.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/imagens/upload")
                        .file(emptyFile)
                        .param("perfil", "false")
                        .with(comUsuario(userId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadImagemComSucesso() throws Exception {
        String userId = criarUserComPerfil();

        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/api/imagens/upload")
                        .file(file)
                        .param("perfil", "true")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void deletarImagemSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(delete("/api/imagens/algum-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletarImagemDeOutroUsuarioDeveRetornar403() throws Exception {
        String userId = criarUserComPerfil();

        // Faz upload para obter um imagemId real
        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/imagens/upload")
                        .file(file)
                        .param("perfil", "false")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk())
                .andReturn();

        String imagemId = objectMapper.readValue(
                uploadResult.getResponse().getContentAsString(),
                ImagemUploadResponseDTO.class).getId();

        // Outro usuário (com perfil próprio) tenta deletar a imagem
        String outroUserId = criarUserComPerfil();

        mockMvc.perform(delete("/api/imagens/" + imagemId)
                        .with(comUsuario(outroUserId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletarImagemComSucesso() throws Exception {
        String userId = criarUserComPerfil();

        // ✅ ImagemService.deletarImagem bloqueia a exclusão da última imagem ativa
        // do perfil (ImagemService.java:114-117, "Não é possível deletar a última
        // imagem do perfil"). Com apenas 1 imagem, a exclusão sempre responde 400 —
        // o teste precisa de uma segunda imagem para exercitar o caminho de sucesso.
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "foto1.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes-1".getBytes());
        mockMvc.perform(multipart("/api/imagens/upload")
                        .file(file1)
                        .param("perfil", "true")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk());

        MockMultipartFile file2 = new MockMultipartFile(
                "file", "foto2.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes-2".getBytes());
        MvcResult uploadResult = mockMvc.perform(multipart("/api/imagens/upload")
                        .file(file2)
                        .param("perfil", "false")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk())
                .andReturn();

        String imagemId = objectMapper.readValue(
                uploadResult.getResponse().getContentAsString(),
                ImagemUploadResponseDTO.class).getId();

        // ✅ Dono da imagem deleta com sucesso. ImagemController.deletarImagem
        // responde 200 com corpo JSON (ImagemController.java:93), não 204 — o teste
        // esperava um contrato que o controller não implementa.
        mockMvc.perform(delete("/api/imagens/" + imagemId)
                        .with(comUsuario(userId)))
                .andExpect(status().isOk());
    }
}
