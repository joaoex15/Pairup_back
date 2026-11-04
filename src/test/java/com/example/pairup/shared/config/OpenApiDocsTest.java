package com.example.pairup.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUiHtmlNaoRedirecionaParaOAuth() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("swagger-ui/index.html")));
    }

    @Test
    void apiDocsDeclaraBearerEDocumentaImagens() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/imagens/upload'].post.summary").value("Enviar foto"))
                .andExpect(jsonPath("$.paths['/api/imagens/{imagemId}'].delete.responses['403']").exists())
                // Rotas públicas não pedem o token no Swagger
                .andExpect(jsonPath("$.paths['/users/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/tags/ativas'].get.security").isEmpty());
    }
}
