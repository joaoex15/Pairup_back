package com.example.Tinder_ufs.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtFilter.class);
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    // ✅ Rotas públicas (não precisam de token)
    private static final List<PublicRoute> PUBLIC_ROUTES = List.of(
            new PublicRoute("POST", "/users"),
            new PublicRoute("POST", "/users/login"),
            new PublicRoute("GET", "/tags"),
            new PublicRoute("GET", "/tags/ativas"),
            // ✅ Rotas do Swagger/OpenAPI
            new PublicRoute("GET", "/swagger-ui/**"),
            new PublicRoute("GET", "/v3/api-docs/**"),
            new PublicRoute("GET", "/api-docs/**"),
            // ✅ Rotas OAuth2
            new PublicRoute("GET", "/oauth2/**"),
            new PublicRoute("POST", "/oauth2/**"),
            new PublicRoute("GET", "/login/oauth2/**"),
            new PublicRoute("POST", "/login/oauth2/**")
    );

    private record PublicRoute(String method, String path) {}

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String requestPath = request.getRequestURI();
        final String requestMethod = request.getMethod();

        // ✅ Verificar se é rota pública
        if (isPublicRoute(requestMethod, requestPath)) {
            log.debug("Rota pública - pulando autenticação: {} {}", requestMethod, requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        // ✅ ✅ CORREÇÃO: Rotas do proxy de imagens exigem token
        // (já estão protegidas por não estarem na lista de públicas)

        final String authHeader = request.getHeader("Authorization");

        // ✅ Rota protegida sem token = 401 UNAUTHORIZED
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("❌ Rota protegida sem token: {} {}", requestMethod, requestPath);
            sendUnauthorizedResponse(response, "Token não fornecido");
            return;
        }

        try {
            final String token = authHeader.substring(7);

            // ✅ Verificar se o token é válido
            if (!jwtService.isTokenValid(token)) {
                log.warn("❌ Token inválido para rota: {} {}", requestMethod, requestPath);
                sendUnauthorizedResponse(response, "Token inválido");
                return;
            }

            final String userId = jwtService.extractUserId(token);

            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userId);

                // ✅ Usar o método que valida token com UserDetails
                if (jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("✅ Usuário autenticado: {}", userId);
                } else {
                    log.warn("❌ Token inválido para usuário: {}", userId);
                    sendUnauthorizedResponse(response, "Token inválido para este usuário");
                    return;
                }
            }

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.error("❌ Erro ao processar token JWT: {}", e.getMessage());
            sendUnauthorizedResponse(response, "Token inválido ou expirado");
        }
    }

    private boolean isPublicRoute(String method, String path) {
        // ✅ Verificação exata para rotas sem wildcard
        for (PublicRoute route : PUBLIC_ROUTES) {
            if (route.method().equalsIgnoreCase(method)) {
                if (route.path().endsWith("/**")) {
                    // ✅ Suporte para wildcard (ex: /swagger-ui/**)
                    String basePath = route.path().substring(0, route.path().length() - 3);
                    if (path.startsWith(basePath)) {
                        return true;
                    }
                } else if (matchesPath(route.path(), path)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesPath(String routePath, String requestPath) {
        return routePath.equals(requestPath);
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format("{\"erro\": \"%s\"}", message));
    }
}