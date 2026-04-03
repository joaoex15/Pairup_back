package com.example.Tinder_ufs.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@AllArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public static final String HEADER = "Authorization";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HEADER);

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            try {
                String userId = jwtService.extractUserId(token);

                // ✅ CORREÇÃO CRÍTICA: registrar autenticação no SecurityContextHolder
                // Sem isso o Spring Security não sabe que o usuário está autenticado,
                // mesmo que o filtro tenha extraído o userId com sucesso.
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, List.of());

                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Mantém o atributo para uso nos controllers (ex: request.getAttribute("userId"))
                request.setAttribute("userId", userId);

            } catch (Exception e) {
                // Token inválido ou expirado: limpa contexto e retorna 401
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Token inválido ou expirado\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}