package com.example.pairup.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        // Habilita o botão "Authorize" do Swagger UI (token de POST /users/login)
        return new OpenAPI()
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER))
                .info(new Info()
                        .title("PairUp API")
                        .description("API do aplicativo PairUp - Documentação dos endpoints")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipe PairUp")
                                .email("contato@pairup.com"))
                        .license(new License()
                                .name("Licença - UFS")
                                .url("https://ufs.br")));
    }
}