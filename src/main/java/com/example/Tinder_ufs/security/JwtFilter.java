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
            new PublicRoute("POST", "/users"),           // Criar usuário
            new PublicRoute("POST", "/users/login"),     // Login
            new PublicRoute("GET", "/tags"),             // Listar tags
            new PublicRoute("GET", "/tags/ativas")       // Listar tags ativas
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
        return PUBLIC_ROUTES.stream()
                .anyMatch(route -> route.method().equalsIgnoreCase(method) && matchesPath(route.path(), path));
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