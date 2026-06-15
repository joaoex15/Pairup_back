package com.example.Tinder_ufs.config;

import com.example.Tinder_ufs.security.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // ✅ Rotas públicas (não exigem token)
                        .requestMatchers(
                                "/users/login",
                                "/users",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/swagger-ui/**",
                                "/api-docs/**",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // ✅ Tags: GET público, POST exige autenticação
                        .requestMatchers(HttpMethod.GET, "/tags", "/tags/ativas").permitAll()
                        .requestMatchers(HttpMethod.POST, "/tags").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/tags/**").hasRole("ADMIN")

                        // ✅ ✅ CORREÇÃO CRÍTICA: Rotas do proxy de imagens exigem autenticação
                        .requestMatchers("/api/imagens/proxy/**").authenticated()

                        // ✅ Rotas de imagens exigem autenticação
                        .requestMatchers("/api/imagens/**").authenticated()

                        // ✅ Rotas de perfil exigem autenticação
                        .requestMatchers("/pessoas/**").authenticated()

                        // ✅ Rotas de likes e matches exigem autenticação
                        .requestMatchers("/likes/**").authenticated()
                        .requestMatchers("/matches/**").authenticated()

                        // ✅ Qualquer outra requisição exige autenticação
                        .anyRequest().authenticated()
                )

                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                )

                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:3000",
                "http://localhost:8080",
                "https://pairup-flax.vercel.app",
                "https://pairup-production-b9ee.up.railway.app"
        ));

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}