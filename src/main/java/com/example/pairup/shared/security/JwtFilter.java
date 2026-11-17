package com.example.pairup.shared.security;

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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

    // Rotas públicas (não precisam de token). A autorização final é do SecurityConfig.
    private static final List<PublicRoute> PUBLIC_ROUTES = List.of(
            new PublicRoute("POST", "/users"),
            new PublicRoute("POST", "/users/login"),
            new PublicRoute("GET", "/tags"),
            new PublicRoute("GET", "/tags/ativas"),
            new PublicRoute("GET", "/swagger-ui.html"),
            new PublicRoute("GET", "/swagger-ui/**"),
            new PublicRoute("GET", "/v3/api-docs/**"),
            new PublicRoute("GET", "/api-docs/**"),
            new PublicRoute("GET", "/api-docs"),
            new PublicRoute("GET", "/oauth2/**"),
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

        // Preflight CORS nunca leva token
        if ("OPTIONS".equalsIgnoreCase(requestMethod) || isPublicRoute(requestMethod, requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendUnauthorizedResponse(response, "Token não fornecido");
            return;
        }

        // Só a autenticação fica no try: antes o doFilter também estava dentro, e qualquer
        // erro do controller virava "401 Token inválido", escondendo o erro real.
        try {
            final String token = authHeader.substring(7).trim();
            final String userId = jwtService.extractUserId(token);

            if (userId == null) {
                sendUnauthorizedResponse(response, "Token inválido ou expirado");
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                // Conta apagada depois de emitido o token → UsernameNotFoundException → 401
                UserDetails userDetails = userDetailsService.loadUserByUsername(userId);

                if (!jwtService.isTokenValid(token, userDetails)) {
                    sendUnauthorizedResponse(response, "Token inválido ou expirado");
                    return;
                }

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (UsernameNotFoundException e) {
            sendUnauthorizedResponse(response, "Token inválido ou expirado");
            return;
        } catch (RuntimeException e) {
            log.warn("Erro ao processar token JWT: {}", e.getClass().getSimpleName());
            SecurityContextHolder.clearContext();
            sendUnauthorizedResponse(response, "Token inválido ou expirado");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicRoute(String method, String path) {
        for (PublicRoute route : PUBLIC_ROUTES) {
            if (!route.method().equalsIgnoreCase(method)) continue;
            if (route.path().endsWith("/**")) {
                // "/swagger-ui/**" casa "/swagger-ui/..." mas não "/swagger-uiX"
                String basePath = route.path().substring(0, route.path().length() - 2);
                if (path.startsWith(basePath)) return true;
            } else if (route.path().equals(path)) {
                return true;
            }
        }
        return false;
    }

    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"erro\": \"" + message + "\"}");
    }
}
