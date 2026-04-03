package com.example.Tinder_ufs.controller;

import com.example.Tinder_ufs.models.User;
import com.example.Tinder_ufs.models.Pessoa;
import com.example.Tinder_ufs.dto.ImagemUploadResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ImagemControllerTest {

    User USER = new User("João", "joao_imagem@email.com", "123456");

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    Pessoa PESSOA = new Pessoa("João", "Engenharia", LocalDate.parse("01/01/2001", formatter), "joao_imagem@email.com");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Simula o JwtFilter injetando o userId como atributo da request.
     */
    private RequestPostProcessor comUsuario(String userId) {
        return (MockHttpServletRequest request) -> {
            request.setAttribute("userId", userId);
            return request;
        };
    }

    /**
     * Cria um User e um Pessoa associado, retornando o userId.
     */
    private String criarUserComPerfil() throws Exception {
        MvcResult userResult = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(USER)))
                .andExpect(status().isOk())
                .andReturn();
        String userId = objectMapper.readValue(
                userResult.getResponse().getContentAsString(), User.class).getId();

        mockMvc.perform(post("/pessoas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(PESSOA)))
                .andExpect(status().isOk());

        return userId;
    }

    @Test
    void uploadImagemSemTokenDeveRetornar401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/imagens/upload")
                        .file(file)
                        .param("perfil", "true"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadImagemSemPerfilCadastradoDeveRetornar404() throws Exception {
        // User existe mas não tem Pessoa associada
        User userSemPerfil = new User("Sem Perfil", "semperfil_imagem@email.com", "123456");
        MvcResult userResult = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userSemPerfil)))
                .andExpect(status().isOk())
                .andReturn();
        String userId = objectMapper.readValue(
                userResult.getResponse().getContentAsString(), User.class).getId();

        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/imagens/upload")
                        .file(file)
                        .param("perfil", "true")
                        .with(comUsuario(userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadImagemComArquivoVazioDeveRetornar400() throws Exception {
        String userId = criarUserComPerfil();

        // Arquivo vazio
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "vazio.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[0]);

        mockMvc.perform(multipart("/imagens/upload")
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

        mockMvc.perform(multipart("/imagens/upload")
                        .file(file)
                        .param("perfil", "true")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void deletarImagemSemTokenDeveRetornar401() throws Exception {
        mockMvc.perform(delete("/imagens/algum-id"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletarImagemDeOutroUsuarioDeveRetornar403() throws Exception {
        String userId = criarUserComPerfil();

        // Faz upload para obter um imagemId real
        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/imagens/upload")
                        .file(file)
                        .param("perfil", "false")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk())
                .andReturn();

        String imagemId = objectMapper.readValue(
                uploadResult.getResponse().getContentAsString(),
                ImagemUploadResponseDTO.class).getId();

        // Outro usuário tenta deletar a imagem
        User outroUser = new User("Outro", "outro_imagem@email.com", "123456");
        MvcResult outroResult = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(outroUser)))
                .andExpect(status().isOk())
                .andReturn();
        String outroUserId = objectMapper.readValue(
                outroResult.getResponse().getContentAsString(), User.class).getId();

        mockMvc.perform(delete("/imagens/" + imagemId)
                        .with(comUsuario(outroUserId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletarImagemComSucesso() throws Exception {
        String userId = criarUserComPerfil();

        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-image-bytes".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/imagens/upload")
                        .file(file)
                        .param("perfil", "false")
                        .with(comUsuario(userId)))
                .andExpect(status().isOk())
                .andReturn();

        String imagemId = objectMapper.readValue(
                uploadResult.getResponse().getContentAsString(),
                ImagemUploadResponseDTO.class).getId();

        // ✅ Dono da imagem deleta com sucesso
        mockMvc.perform(delete("/imagens/" + imagemId)
                        .with(comUsuario(userId)))
                .andExpect(status().isNoContent());
    }
}