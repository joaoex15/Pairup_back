package com.example.pairup.shared.config;

import com.example.pairup.shared.security.JwtFilter;
import com.example.pairup.shared.security.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Autowired
    private JwtFilter jwtFilter;

    @Autowired
    private RateLimitFilter rateLimitFilter;

    @Value("${FRONTEND_URL:http://localhost:5173}")
    private String frontendUrl;

    /** Lista separada por vírgula. Sobrescreva com CORS_ALLOWED_ORIGINS em produção. */
    @Value("${seguranca.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${springdoc.swagger-ui.enabled:false}")
    private boolean swaggerHabilitado;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // API stateless com Bearer token no header: não há cookie de sessão para CSRF explorar
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())

                // STATELESS: a autenticação nunca fica salva em sessão/cookie, só o JWT vale.
                // (O fluxo OAuth2 ainda usa a sessão só para guardar o "state" entre redirect e callback.)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .headers(headers -> headers
                        .contentTypeOptions(c -> {})
                        .frameOptions(f -> f.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicy(p -> p.policy("camera=(), microphone=(), geolocation=()"))
                )

                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers(HttpMethod.POST, "/users", "/users/login").permitAll();
                    auth.requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll();
                    auth.requestMatchers("/error").permitAll();

                    if (swaggerHabilitado) {
                        auth.requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**",
                                "/api-docs", "/api-docs/**", "/v3/api-docs/**").permitAll();
                    }

                    // Tags: leitura pública; escrita só ADMIN (nenhuma conta tem ADMIN hoje, as tags
                    // são cadastradas direto no banco). Antes qualquer usuário criava tags e, enviando
                    // o "id" de uma existente, sobrescrevia/desativava a tag de todo mundo.
                    auth.requestMatchers(HttpMethod.GET, "/tags", "/tags/ativas").permitAll();
                    auth.requestMatchers("/tags", "/tags/**").hasRole("ADMIN");

                    auth.anyRequest().authenticated();
                })

                // Sem token / token inválido: 401 JSON (e não redirect para a tela de login do Google)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                escreverErro(res, HttpServletResponse.SC_UNAUTHORIZED, "Não autenticado"))
                        .accessDeniedHandler((req, res, e) ->
                                escreverErro(res, HttpServletResponse.SC_FORBIDDEN, "Acesso negado"))
                )

                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler((req, res, e) ->
                                res.sendRedirect(baseFrontend() + "/?erro=google_falhou"))
                )

                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtFilter, RateLimitFilter.class);

        return http.build();
    }

    /** Os filtros são @Component: sem isto o Spring Boot os registraria uma segunda vez fora da cadeia. */
    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistration(JwtFilter filter) {
        FilterRegistrationBean<JwtFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origens = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !"*".equals(s))
                .toList();
        config.setAllowedOrigins(origens);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        // O front usa credentials: 'omit' (token vai no header), então cookies não precisam cruzar origem
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private String baseFrontend() {
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    private static void escreverErro(HttpServletResponse res, int status, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"erro\": \"" + msg + "\"}");
    }
}
