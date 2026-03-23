package com.example.Tinder_ufs.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tinder UFS API")
                        .description("API do aplicativo Tinder UFS - Documentação dos endpoints")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Equipe Tinder UFS")
                                .email("contato@tinderufs.com"))
                        .license(new License()
                                .name("Licença - UFS")
                                .url("https://ufs.br")));
    }
}